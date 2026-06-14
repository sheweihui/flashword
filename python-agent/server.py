#!/usr/bin/env python3
"""
背单词助手 Agent — FastAPI 服务

启动:
    python server.py
    uvicorn server:app --host 0.0.0.0 --port 8000

前端通过 /agent/chat 接口与 AI 对话。
"""

import json
import re
import sys
import time
import uuid
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Optional

from fastapi import FastAPI, HTTPException, Request, UploadFile, File, Form
from fastapi.middleware.cors import CORSMiddleware
from loguru import logger

from config.settings import (
    AGENT_HOST, AGENT_PORT, LOG_LEVEL, LOG_FILE,
    AGENT_NAME, AGENT_VERSION, LLM_API_KEY,
    TOOL_CALL_MAX_ROUNDS, CONVERSATION_MAX_AGE_DAYS,
    EMBEDDING_PROVIDER, EMBEDDING_MODEL, EMBEDDING_DIMS,
    SPLITTER_PROVIDER, CHUNK_SIZE, CHUNK_OVERLAP,
    HYBRID_SEARCH_ENABLED, HYBRID_SEARCH_TOP_K_DENSE,
    HYBRID_SEARCH_TOP_K_SPARSE, HYBRID_SEARCH_TOP_K_FUSION,
    HYBRID_SEARCH_RRF_K, BM25_INDEX_DIR,
)
from api.client import ApiClient
from api.endpoints import Endpoints
from api.schemas import (
    ChatRequest, ChatResponse, WordEnrichRequest, WordEnrichResponse,
    KnowledgeUploadRequest, KnowledgeUploadResponse, KnowledgeDocument,
)
from agent.llm import LLMClient
from agent.rag import RAGRetriever
from agent.conversation import ConversationManager
from agent.mcp_client import MCPClient
from agent.knowledge_base import KnowledgeBase
from agent.recommender import RecommendEngine
from agent.context_optimizer import ContextOptimizer
from agent.indexer import Indexer
from agent.degradation import DegradationManager, Component, FallbackStrategy
from agent.embeddings import BaseEmbedding, EmbeddingFactory, ONNXEmbedding, OpenAIEmbedding, OllamaEmbedding
from agent.splitters import SplitterFactory, RecursiveSplitter, FixedSplitter
from agent.retrieval import BM25Indexer, DenseRetriever, SparseRetriever, RRFFusion, HybridSearch
from agent.loader import load_file, get_title_from_filename, list_supported_formats
from agent.trace import TraceContext
from agent.query_processor import QueryProcessor

# ------------------------------------------------------------
# 全局组件
# ------------------------------------------------------------
api_client: ApiClient = None
api_endpoints: Endpoints = None
llm: LLMClient = None
rag: RAGRetriever = None
conversations: ConversationManager = None
mcp_client: MCPClient = None
kb: KnowledgeBase = None
recommender: RecommendEngine = None
context_optimizer: ContextOptimizer = None
indexer: Indexer = None
degradation: DegradationManager = None
query_processor: QueryProcessor = None


def _create_embedding_factory() -> EmbeddingFactory:
    """创建并注册嵌入提供者。"""
    factory = EmbeddingFactory()
    factory.register("onnx", ONNXEmbedding)
    factory.register("openai", OpenAIEmbedding)
    factory.register("ollama", OllamaEmbedding)

    logger.info(f"嵌入提供者已注册: {factory.list_providers()}")
    return factory


def _create_splitter_factory() -> SplitterFactory:
    factory = SplitterFactory()
    factory.register("recursive", RecursiveSplitter)
    factory.register("fixed", FixedSplitter)
    return factory


def _build_embedding(factory: EmbeddingFactory) -> BaseEmbedding:
    """根据配置创建嵌入提供者实例。"""
    provider = EMBEDDING_PROVIDER.lower()

    kwargs = {"model": EMBEDDING_MODEL}
    if provider == "onnx":
        pass  # ONNXEmbedding 不需要额外参数
    elif provider == "openai":
        kwargs["dimensions"] = EMBEDDING_DIMS
    elif provider == "ollama":
        pass
    else:
        logger.warning(f"不支持的嵌入提供者 '{provider}'，回退到 ONNX")
        provider = "onnx"

    emb = factory.create(provider, **kwargs)
    logger.info(f"嵌入后端已初始化: {emb.name}/{EMBEDDING_MODEL} (维度: {emb.get_dimension()})")
    return emb


def init_components():
    global api_client, api_endpoints, llm, rag, conversations, kb, recommender, context_optimizer, indexer, degradation, query_processor
    api_client = ApiClient()
    api_endpoints = Endpoints(api_client)

    # 初始化查询处理器
    query_processor = QueryProcessor()

    # 初始化可插拔嵌入
    emb_factory = _create_embedding_factory()
    embedding = _build_embedding(emb_factory)

    # 初始化可插拔分块器
    splitter_factory = _create_splitter_factory()
    splitter = splitter_factory.create(
        SPLITTER_PROVIDER,
        chunk_size=CHUNK_SIZE,
        chunk_overlap=CHUNK_OVERLAP,
    )
    logger.info(f"分块器已初始化: {splitter.name} (chunk_size={CHUNK_SIZE}, overlap={CHUNK_OVERLAP})")

    # 初始化混合搜索
    hs = None
    bm25 = None
    if HYBRID_SEARCH_ENABLED:
        try:
            bm25 = BM25Indexer(index_dir=BM25_INDEX_DIR)
            dense = DenseRetriever(
                embedding=embedding,
                collection=kb._collection,
                top_k=HYBRID_SEARCH_TOP_K_DENSE,
            )
            sparse = SparseRetriever(
                bm25_indexer=bm25,
                collection=kb._collection,
                collection_name="knowledge_base",
                top_k=HYBRID_SEARCH_TOP_K_SPARSE,
            )
            fusion = RRFFusion(k=HYBRID_SEARCH_RRF_K)
            hs = HybridSearch(
                dense_retriever=dense,
                sparse_retriever=sparse,
                fusion=fusion,
                dense_top_k=HYBRID_SEARCH_TOP_K_DENSE,
                sparse_top_k=HYBRID_SEARCH_TOP_K_SPARSE,
                fusion_top_k=HYBRID_SEARCH_TOP_K_FUSION,
            )

            # 尝试从已有数据重建 BM25 索引
            bm25_loaded = bm25.load("knowledge_base")
            if not bm25_loaded and kb.chunk_count > 0:
                logger.info("BM25 索引不存在，从现有数据重建...")
                kb.build_bm25_index()
            elif bm25_loaded:
                logger.info(f"BM25 索引已加载 ({len(bm25._index)} 词项)")

            logger.info(f"混合搜索已启用: dense@{HYBRID_SEARCH_TOP_K_DENSE} + "
                        f"sparse@{HYBRID_SEARCH_TOP_K_SPARSE} → "
                        f"RRF({HYBRID_SEARCH_RRF_K}) → top@{HYBRID_SEARCH_TOP_K_FUSION}")
        except Exception as e:
            logger.warning(f"混合搜索初始化失败，回退到纯稠密检索: {e}")
            hs = None
            bm25 = None

    kb = KnowledgeBase(embedding=embedding, splitter=splitter,
                        hybrid_search=hs, bm25_indexer=bm25)

    rag = RAGRetriever(api_endpoints, kb=kb)
    recommender = RecommendEngine(api_endpoints)
    context_optimizer = ContextOptimizer()
    indexer = Indexer(kb=kb, api=api_endpoints)
    degradation = DegradationManager()
    conversations = ConversationManager()

    conversations.clean_expired(max_age_days=CONVERSATION_MAX_AGE_DAYS)

    if LLM_API_KEY and LLM_API_KEY != "sk-your-deepseek-api-key":
        llm = LLMClient()
        logger.info("LLM 已初始化 (DeepSeek)")
    else:
        llm = None
        logger.warning("LLM_API_KEY 未配置，将使用本地模式回复")


def check_backend_connectivity() -> dict:
    """启动时检查后端连通性"""
    results = {}
    try:
        import requests
        resp = requests.get(
            "http://localhost:8080/",
            timeout=3,
            headers={"Accept": "application/json"}
        )
        results["backend"] = f"connected (HTTP {resp.status_code})"
        logger.info(f"后端连通性检查: OK (HTTP {resp.status_code})")
    except requests.ConnectionError:
        results["backend"] = "unreachable: 无法连接到 localhost:8080"
        logger.warning("后端连通性检查: 无法连接 - 请确认后端已启动")
    except Exception as e:
        results["backend"] = f"unreachable: {e}"
        logger.warning(f"后端连通性检查: 异常 ({e})")
    return results


@asynccontextmanager
async def lifespan(app: FastAPI):
    init_components()
    check_backend_connectivity()

    # 初始化 MCP 客户端
    global mcp_client
    try:
        mcp_client = MCPClient()
        await mcp_client.connect()
        degradation.record_success(Component.MCP)
    except Exception as e:
        logger.warning(f"MCP 初始化失败，将使用本地模式: {e}")
        mcp_client = None
        degradation.record_failure(Component.MCP)

    # 无 LLM 只做降级追踪，不影响启动
    if not llm:
        degradation.record_failure(Component.LLM)
        logger.info("LLM 未配置，降级为本地模式")

    # 启动时索引健康检查：记录各组件初始状态
    if kb:
        degradation.record_success(Component.CHROMADB)

    logger.info(f"启动完成 | {degradation.get_report()}")

    yield

    if mcp_client:
        await mcp_client.close()
    logger.info("Agent 服务关闭")


app = FastAPI(
    title=AGENT_NAME,
    version=AGENT_VERSION,
    description="背单词 App AI 助手 — RAG + 对话 + Function Calling",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ------------------------------------------------------------
# 请求中间件（计时 + 请求 ID）
# ------------------------------------------------------------

@app.middleware("http")
async def request_logging(request: Request, call_next):
    request_id = uuid.uuid4().hex[:8]
    start = time.time()
    response = await call_next(request)
    elapsed = (time.time() - start) * 1000
    logger.info(f"[{request_id}] {request.method} {request.url.path} "
                f"→ {response.status_code} ({elapsed:.0f}ms)")
    response.headers["X-Request-ID"] = request_id
    return response


# ------------------------------------------------------------
# Prompt 模板
# ------------------------------------------------------------
SYSTEM_PROMPT = """你是一个专业的英语学习助手，帮助用户背单词、学英语。

## 你的能力
1. 查单词：解释单词含义、音标、词性、例句
2. 推荐学习内容：根据用户水平推荐单词
3. 测试词汇量：出题测试用户
4. 解释句子：分析句子结构和含义
5. 学习建议：提供背单词方法和技巧

## 行为准则
- 回答简洁清晰，使用中文解释
- 涉及单词时标注音标、词性、中文释义
- 适当举例帮助理解
- 鼓励用户，保持积极正面
- 如果不知道答案，诚实告知，不要编造

## 上下文信息（重要：请优先使用以下信息回答）
{context}

当上下文包含以下内容时，请在你的回答中体现出来：
- **## 单词查询** — 直接使用查询结果解释单词含义
- **## 拼写提示** — 用户拼错了，先指出正确拼写再用给出的释义解释
- **## 近义词推荐** — 解释完单词后，主动推荐近义词让用户扩展学习
- **## 用户学习概况** — 结合用户的积分、词本数量等数据给出个性化建议
- **## 单词语义分组** — 按语义组出题或让用户按组复习
- **## 学习推荐** / **## 词书推荐** — 推荐给用户并说明理由
- **## 知识库匹配** — 引用用户上传的知识库内容辅助回答

## 可用操作
当用户要求执行以下操作时，请调用相应的函数（function calling）：
{tool_descriptions}

注意：如果用户只是提问或聊天（比如"hello"、"背单词有什么技巧"），直接回答即可，不需要调用函数。
"""


def _build_system_prompt(context: str = "") -> str:
    """构建带上下文和工具描述的系统提示词"""
    tool_descriptions = mcp_client.get_tool_descriptions() if mcp_client else "暂无可用操作。"
    return SYSTEM_PROMPT.format(
        context=context or "暂无额外上下文。",
        tool_descriptions=tool_descriptions,
    )


# ------------------------------------------------------------
# 路由
# ------------------------------------------------------------

@app.get("/agent/health")
async def health():
    try:
        import psutil
        process = psutil.Process()
        mem = process.memory_info().rss / 1024 / 1024
        uptime = time.time() - process.create_time()
    except ImportError:
        mem = 0
        uptime = 0

    deg_report = degradation.get_report() if degradation else []
    components_status = {d["component"]: d["status"] for d in deg_report}

    rag_cache_size = await rag._cache.size if rag else 0

    return {
        "status": "ok",
        "version": AGENT_VERSION,
        "llm_ready": llm is not None,
        "mcp_ready": mcp_client is not None and mcp_client.connected,
        "knowledge_base": kb is not None and kb.available,
        "uptime_s": round(uptime),
        "memory_mb": round(mem, 1) if mem else 0,
        "rag_cache_size": rag_cache_size,
        "indexer_stats": indexer.get_stats() if indexer else {},
        "degradation": {
            "components": components_status,
            "report": deg_report,
        },
    }


# ------------------------------------------------------------
# 知识库 API
# ------------------------------------------------------------


@app.post("/agent/knowledge/upload",
          response_model=KnowledgeUploadResponse)
def upload_knowledge(req: KnowledgeUploadRequest):
    """上传文档到知识库"""
    if not kb or not kb.available:
        raise HTTPException(status_code=503, detail="知识库不可用")

    if not req.title.strip():
        raise HTTPException(status_code=400, detail="标题不能为空")
    if not req.content.strip():
        raise HTTPException(status_code=400, detail="内容不能为空")

    result = kb.add_document(req.title, req.content, req.user_id or 0)
    return KnowledgeUploadResponse(
        **result,
        message=f"上传成功，已分块为 {result['chunk_count']} 个片段",
    )


@app.post("/agent/knowledge/upload-file")
async def upload_knowledge_file(
    file: UploadFile = File(...),
    user_id: int = Form(0),
):
    """上传文件到知识库（支持 PDF/TXT/MD）

    使用 multipart/form-data 上传文件，文件会自动解析并入库。
    用法: curl -F "file=@文档.pdf" -F "user_id=1" http://localhost:8000/agent/knowledge/upload-file
    """
    import tempfile

    if not kb or not kb.available:
        raise HTTPException(status_code=503, detail="知识库不可用")

    # 保存上传文件到临时位置
    filename = file.filename or "uploaded.pdf"
    suffix = Path(filename).suffix.lower()

    supported = list_supported_formats()
    if suffix not in supported:
        raise HTTPException(
            status_code=400,
            detail=f"不支持的文件格式: {suffix}（支持: {', '.join(supported)}）",
        )

    try:
        content = await file.read()
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
            tmp.write(content)
            tmp_path = tmp.name
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"文件读取失败: {e}")

    # 提取文本（自动识别格式）
    try:
        doc = load_file(tmp_path)
        title = get_title_from_filename(filename)
        text = doc.text
    except ValueError as e:
        Path(tmp_path).unlink(missing_ok=True)
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        Path(tmp_path).unlink(missing_ok=True)
        raise HTTPException(status_code=500, detail=f"文件解析失败: {e}")

    if not text.strip():
        Path(tmp_path).unlink(missing_ok=True)
        raise HTTPException(status_code=400, detail="文件中未提取到文本内容")

    # 入库
    try:
        result = kb.add_document(title, text, user_id or 0)
    except Exception as e:
        Path(tmp_path).unlink(missing_ok=True)
        raise HTTPException(status_code=500, detail=f"知识库入库失败: {e}")

    Path(tmp_path).unlink(missing_ok=True)

    return {
        "id": result["id"],
        "title": result["title"],
        "chunk_count": result["chunk_count"],
        "message": f"文件「{filename}」上传成功，已分块为 {result['chunk_count']} 个片段",
    }


@app.get("/agent/knowledge/documents")
def list_knowledge_documents():
    """列出知识库中的所有文档"""
    if not kb or not kb.available:
        return {"documents": []}
    docs = kb.list_documents()
    return {"documents": docs}


@app.delete("/agent/knowledge/documents/{doc_id}")
def delete_knowledge_document(doc_id: str):
    """删除知识库中的文档"""
    if not kb or not kb.available:
        raise HTTPException(status_code=503, detail="知识库不可用")
    kb.delete_document(doc_id)
    return {"status": "ok", "doc_id": doc_id}


@app.post("/agent/knowledge/search")
def search_knowledge(query: str, user_id: int = 0, top_k: int = 3):
    """搜索知识库（调试用）"""
    if not kb or not kb.available:
        return {"results": []}
    results = kb.search(query, top_k=top_k, user_id=user_id if user_id else None)
    return {"results": results}


# ------------------------------------------------------------
# 索引 API
# ------------------------------------------------------------


@app.get("/agent/index/stats")
def index_stats():
    """查看索引统计"""
    if not indexer:
        return {"stats": {}}
    return {"stats": indexer.get_stats()}


# ------------------------------------------------------------
# 降级管理 API
# ------------------------------------------------------------


@app.get("/agent/degradation/status")
def degradation_status():
    """查看所有组件的降级状态"""
    if not degradation:
        return {"components": []}
    return {"components": degradation.get_report()}


@app.post("/agent/degradation/reset")
def degradation_reset(component: str = ""):
    """手动重置组件熔断状态"""
    if not degradation:
        raise HTTPException(status_code=503, detail="降级管理器不可用")
    if component:
        degradation.reset_component(component)
        return {"status": "ok", "component": component}
    degradation.reset_all()
    return {"status": "ok", "component": "all"}


@app.get("/agent/recommend/books")
def recommend_books(user_id: int = 0, top_k: int = 5):
    """推荐学习词书"""
    if not recommender:
        return {"recommendations": []}
    try:
        recs = recommender.recommend(user_id=user_id, top_k=top_k)
        return {"recommendations": recs}
    except Exception as e:
        logger.warning(f"推荐接口异常: {e}")
        return {"recommendations": [], "error": str(e)}


@app.post("/agent/chat", response_model=ChatResponse)
async def chat(req: ChatRequest):
    if not req.message.strip():
        raise HTTPException(status_code=400, detail="消息不能为空")

    # 1. Token 注入（同时持久化到 auth.json 供 MCP Server 读取）
    if req.token:
        api_client.auth.set_token(
            token=req.token,
            user_id=req.user_id or 0,
        )
        # 同步写入 auth.json，MCP Server 子进程通过 _reload_auth() 读取
        from api.auth import AuthSession
        api_client.auth.save_session(AuthSession(
            token=req.token,
            user_id=req.user_id or 0,
            username=f"user_{req.user_id or 0}",
        ))

    # 2. 以 user_id 为唯一标识获取或创建对话
    conv_id = conversations.find_by_user(req.user_id) or conversations.create_conversation(user_id=req.user_id)
    conversations.update_metadata(conv_id, user_id=req.user_id)

    user_msg_preview = req.message[:60].replace("\n", " ")
    logger.info(f"[对话 {conv_id}] 用户(user_id={req.user_id}): {user_msg_preview}")

    # 3. 保存用户消息
    conversations.add_message(conv_id, "user", req.message)

    # 4. 查询预处理（意图分类 + 关键词提取）
    processed = query_processor.process(req.message)
    trace.set_metadata("intent", processed.intent)
    trace.set_metadata("keywords", processed.keywords)
    trace.record_stage("query_process", 0, data={
        "intent": processed.intent,
        "keywords": processed.keywords,
        "english_words": processed.english_words,
    })
    logger.info(f"[查询] 意图={processed.intent}, 关键词={processed.keywords}")

    # 5. RAG 检索（带 Trace 追踪）
    trace.set_metadata("user_id", req.user_id)
    trace.set_metadata("conversation_id", conv_id)
    context = await rag.retrieve_context(req.user_id, req.message, trace=trace)

    # 6b. 学习推荐（当用户询问推荐时）
    if any(kw in req.message.lower() for kw in ("推荐", "词书", "学什么", "背什么", "书", "建议")):
        try:
            recs = recommender.recommend(user_id=req.user_id or 0, top_k=5)
            if recs:
                rec_lines = []
                for r in recs:
                    source = "购买" if r.get("source_type") == 2 else "自建"
                    rec_lines.append(
                        f"- **{r['book_name']}** ({r['word_count']} 词, {source})"
                        f" 匹配度: {r['score']:.0%}" if r['score'] > 0
                        else f"- **{r['book_name']}** ({r['word_count']} 词, {source})"
                    )
                context += "\n\n## 学习推荐\n" + "\n".join(rec_lines)
                logger.info(f"已为用户 {req.user_id} 生成 {len(recs)} 条推荐")
        except Exception as e:
            logger.debug(f"推荐生成异常（可忽略）: {e}")

    # 4c. 基于单词语义的词书推荐（RAG：搜词书内部的单词内容）
    words = RAGRetriever._extract_words(req.message)
    meaningful = [w for w in words if len(w) > 2]
    if meaningful:
        try:
            book_recs = recommender.recommend_by_word_content(meaningful, top_k=3)
            if book_recs:
                lines = ["## 词书推荐"]
                for r in book_recs:
                    score_str = f" 匹配度: {r['score']:.0%}" if r['score'] > 0 else ""
                    lines.append(f"- **{r['book_name']}** ({r['word_count']} 词{score_str})")
                context += "\n\n" + "\n".join(lines)
                logger.info(f"语义匹配词书 {len(book_recs)} 本: {meaningful}")
        except Exception as e:
            logger.debug(f"词书内容推荐异常（可忽略）: {e}")

    # 4e. 上下文优化：压缩、排序、去噪
    raw_context = context
    if context_optimizer and context.strip():
        t0 = time.time()
        context = context_optimizer.optimize(
            context,
            user_message=req.message,
            max_tokens=1500,
            mode="balanced",
        )
        opt_elapsed = (time.time() - t0) * 1000
        stats = context_optimizer.get_stats(raw_context, context)
        trace.record_stage("context_optimize", opt_elapsed, data={
            "before_tokens": stats["before_tokens"],
            "after_tokens": stats["after_tokens"],
            "sections_removed": stats["sections_removed"],
            "compression_ratio": stats["compression_ratio"],
        })
        if stats["sections_removed"] > 0 or stats["compression_ratio"] > 0.1:
            logger.info(f"上下文优化: {stats['before_tokens']}→{stats['after_tokens']} tokens, "
                        f"{stats['sections_removed']} 段移除, "
                        f"压缩比 {stats['compression_ratio']:.0%}")

    # 5. 无 LLM 降级（基于 degradation 策略）
    if not llm:
        logger.warning(f"[对话 {conv_id}] LLM 不可用，降级至本地模式")
        reply = _local_fallback(req.message, context)
        conversations.add_message(conv_id, "assistant", reply)
        return ChatResponse(reply=reply, conversation_id=conv_id)

    llm_strategy = degradation.get_strategy(Component.LLM) if degradation else FallbackStrategy.NORMAL
    if llm_strategy == FallbackStrategy.LOCAL_ONLY:
        logger.warning(f"[对话 {conv_id}] LLM 熔断中，使用本地降级")
        reply = _local_fallback(req.message, context)
        conversations.add_message(conv_id, "assistant", reply)
        return ChatResponse(reply=reply, conversation_id=conv_id)

    # 6. 构建消息 + 工具定义
    history = conversations.get_history(conv_id)
    messages = [{"role": m["role"], "content": m["content"]} for m in history]
    tool_defs = mcp_client.get_tool_defs() if mcp_client else []
    system_prompt = _build_system_prompt(context)

    # 7. Tool-calling 循环
    final_reply = ""
    for rnd in range(1, TOOL_CALL_MAX_ROUNDS + 1):
        # LLM 调用 + 降级追踪
        try:
            llm_t0 = time.time()
            content, tool_calls = llm.chat_with_tools(messages, system_prompt, tool_defs)
            llm_elapsed = (time.time() - llm_t0) * 1000
            if degradation:
                degradation.record_success(Component.LLM, llm_elapsed)
        except Exception as e:
            if degradation:
                degradation.record_failure(Component.LLM)
            logger.error(f"[对话 {conv_id}] LLM 调用异常: {e}")
            continue

        if not tool_calls:
            final_reply = content
            break

        logger.info(f"[对话 {conv_id}] 第 {rnd} 轮工具调用: {len(tool_calls)} 个")

        for tc in tool_calls:
            try:
                fn_name = tc["function"]["name"]
                fn_args = _safe_parse_json(tc["function"]["arguments"])
            except (KeyError, json.JSONDecodeError) as e:
                logger.warning(f"解析 tool_call 失败: {e}")
                continue

            # MCP 调用 + 降级追踪
            if degradation and not degradation.should_try(Component.MCP):
                logger.warning(f"MCP 熔断中，跳过工具 {fn_name}")
                result = f"操作暂不可用（{fn_name} 服务降级中）"
            else:
                try:
                    logger.info(f"  执行工具: {fn_name}({fn_args})")
                    result = await mcp_client.call_tool(fn_name, fn_args)
                    if degradation:
                        degradation.record_success(Component.MCP)
                except Exception as e:
                    if degradation:
                        degradation.record_failure(Component.MCP)
                    logger.warning(f"  MCP 工具调用失败: {e}")
                    result = f"操作执行失败: {e}"

            assistant_msg = {"role": "assistant", "content": content if content else None}
            if tc:
                assistant_msg["tool_calls"] = [tc]
            messages.append(assistant_msg)

            messages.append({
                "role": "tool",
                "tool_call_id": tc["id"],
                "content": result,
            })

    if not final_reply:
        logger.warning(f"[对话 {conv_id}] 工具循环结束但未获得回复")
        final_reply = "抱歉，处理请求时出现了问题，请稍后再试。"

    # 8. 保存回复
    conversations.add_message(conv_id, "assistant", final_reply)

    # Trace 结束
    trace.set_metadata("final_reply_len", len(final_reply))
    trace.finish()

    # 9. 首次消息推断话题
    try:
        conv_data = conversations._load(conv_id)
        if conv_data and conv_data.get("message_count", 0) <= 2:
            topic = _detect_topic(req.message)
            if topic:
                conversations.update_metadata(conv_id, topic=topic)
    except Exception:
        pass

    logger.info(f"[对话 {conv_id}] AI: {final_reply[:80]}...")
    return ChatResponse(reply=final_reply, conversation_id=conv_id)

@app.post("/agent/word/enrich", response_model=WordEnrichResponse)
def enrich_word(req: WordEnrichRequest):
    word_text = req.word_text.strip()
    if not word_text:
        raise HTTPException(status_code=400, detail="单词不能为空")
    if not llm:
        raise HTTPException(status_code=503, detail="LLM 不可用，无法补全单词")

    logger.info(f"[单词补全] {word_text} (user_id={req.user_id})")

    prompt = (
        f"请提供以下英文单词的详细信息，以JSON格式返回：\n"
        f"单词：{word_text}\n\n"
        f"要求返回以下字段：\n"
        f"- wordText: 单词本身\n"
        f"- phonetic: 音标（使用国际音标）\n"
        f"- partOfSpeech: 词性（如 n., v., adj., adv. 等）\n"
        f"- definition: 中文释义（简洁明了）\n"
        f"- exampleSentence: 英文例句（简单易懂）\n"
        f"- exampleTranslation: 例句的中文翻译\n\n"
        f"只返回JSON数据，不要有其他文字说明。"
    )

    reply = llm.chat(
        messages=[{"role": "user", "content": prompt}],
        system_prompt="你是一个单词学习助手，必须只返回纯JSON格式数据。"
    )

    logger.info(f"[单词补全] {word_text} → {reply[:100]}...")
    return WordEnrichResponse(content=reply, word_text=word_text)


@app.get("/agent/conversations/{conv_id}/history")
def get_history(conv_id: str):
    messages = conversations.get_history(conv_id, limit=50)
    return {"conversation_id": conv_id, "messages": messages}


@app.delete("/agent/conversations/{conv_id}")
def clear_conversation(conv_id: str):
    conversations.clear(conv_id)
    return {"status": "ok"}


# ------------------------------------------------------------
# 辅助函数
# ------------------------------------------------------------

def _safe_parse_json(text: str) -> dict:
    try:
        return json.loads(text)
    except (json.JSONDecodeError, TypeError):
        return {}


def _detect_topic(message: str) -> str:
    m = message.lower()

    if re.search(r"[a-z]{3,}", m) and not re.match(
        r"^(你好|hello|hi|help|嗨|哈喽|您好)$", m.strip()
    ):
        if any(kw in m for kw in ("意思", "什么", "翻译", "怎么读", "发音", "单词")):
            return "单词查询"
        return "英语学习"

    if "签到" in m or "checkin" in m or "打卡" in m:
        return "每日签到"
    if "积分" in m or "points" in m or "余额" in m:
        return "积分查询"
    if "单词本" in m or "book" in m or "词书" in m:
        return "单词本管理"
    if "秒杀" in m or "flash" in m or "抢购" in m:
        return "秒杀活动"
    if "建议" in m or "方法" in m or "技巧" in m or "怎么学" in m:
        return "学习建议"
    if "测试" in m or "题目" in m or "考" in m:
        return "词汇测试"

    return "日常对话"


# ------------------------------------------------------------
# 本地回退（无 LLM 时使用）
# ------------------------------------------------------------
def _local_fallback(message: str, context: str = "") -> str:
    msg = message.lower()

    if context and ("单词:" in context or "释义:" in context):
        return f"我找到了相关信息：\n\n{context}\n\n还想了解其他单词吗？"

    if "hello" in msg or "hi" in msg or "你好" in msg:
        return ("你好！我是你的英语学习助手。我可以帮你查单词、"
                "推荐学习内容、测试词汇量。请问有什么可以帮你的？")

    if any(kw in msg for kw in ("建议", "怎么学", "方法", "如何背")):
        return ("背单词小建议：\n\n"
                "1. 少量多次：每天背 10-15 个新词，不要贪多\n"
                "2. 结合例句：把单词放到句子里记，不要死记硬背\n"
                "3. 定期复习：第1天、第3天、第7天、第30天复习\n"
                "4. 多感官结合：看拼写、听发音、写下来、读出来\n"
                "5. 用起来：试着用新学的单词造句或写日记\n\n"
                "需要我帮你制定学习计划吗？")

    if "测试" in msg or "题目" in msg or "考考" in msg:
        return ("好的！来测试一下你的词汇量：\n\n"
                "'beautiful' 是什么意思？\n\n"
                "A. 聪明的\nB. 美丽的\nC. 勇敢的\nD. 善良的\n\n"
                "告诉我你的答案！")

    if "解释" in msg or "句子" in msg:
        return ("你可以发一个英文句子给我，我来帮你分析句子结构、"
                "解释每个单词的含义和语法作用！比如：\n"
                '"The quick brown fox jumps over the lazy dog."')

    if context:
        return f"根据你的学习数据：\n\n{context}\n\n有什么具体想了解的吗？"

    return ("收到你的消息了！你可以：\n\n"
            "* 查单词：直接输入英文单词\n"
            "* 学英语：输入'学习建议'\n"
            "* 测词汇：输入'测试'\n"
            "* 分析句子：发送一个英文句子\n\n"
            "有什么我能帮你的吗？")


# ------------------------------------------------------------
# 入口
# ------------------------------------------------------------
def main():
    logger.remove()
    logger.add(sys.stderr, level=LOG_LEVEL, format="<level>{level:7}</level> | {message}")
    logger.add(LOG_FILE, rotation="10 MB", level="DEBUG")

    import uvicorn
    logger.info(f"启动 {AGENT_NAME} v{AGENT_VERSION} → http://{AGENT_HOST}:{AGENT_PORT}")
    uvicorn.run(app, host=AGENT_HOST, port=AGENT_PORT)


if __name__ == "__main__":
    main()
