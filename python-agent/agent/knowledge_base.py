"""
知识库管理 — 文档向量化存储 + 语义检索

使用 chromadb 实现向量检索，支持可插拔嵌入后端（ONNX / OpenAI / Ollama）。

用户上传文章/笔记 → 分块 → chromadb 自动 embedding → 持久化到磁盘
RAG 检索时自动搜知识库，返回相关片段作为上下文。
"""

import json
import uuid
from pathlib import Path
from typing import Optional

from loguru import logger

import chromadb

from agent.embeddings import BaseEmbedding, ONNXEmbedding
from agent.splitters import BaseSplitter, RecursiveSplitter
from agent.retrieval import (
    BM25Indexer, DenseRetriever, SparseRetriever,
    RRFFusion, HybridSearch, RetrievalResult,
)

# ---------- 配置 ----------
DATA_DIR = Path(__file__).resolve().parent.parent / "data"
CHROMA_DIR = DATA_DIR / "chroma_db"
DOCS_INDEX = DATA_DIR / "knowledge_docs.json"
CHUNK_SIZE = 500      # 每块字符数
CHUNK_OVERLAP = 50    # 块之间重叠字符数

# ---------- 文档注册表 ----------
# 轻量 JSON 文件，记录 doc_id → {title, chunk_count, user_id}
# chromadb 存向量和文本，这个文件管文档元数据


def _load_docs() -> dict[str, dict]:
    if DOCS_INDEX.exists():
        try:
            with open(DOCS_INDEX, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            return {}
    return {}


def _save_docs(docs: dict[str, dict]):
    DOCS_INDEX.parent.mkdir(parents=True, exist_ok=True)
    with open(DOCS_INDEX, "w", encoding="utf-8") as f:
        json.dump(docs, f, ensure_ascii=False, indent=2)


# ============================================================
# ChromaDB 嵌入函数适配器
# ============================================================


def _create_chromadb_ef(embedding: BaseEmbedding):
    """将异步 BaseEmbedding 包装为 chromadb 同步嵌入函数。

    chromadb 的 Collection 需要同步的 embedding_function，
    内部使用 asyncio.to_thread / run 来桥接异步调用。
    """
    import asyncio
    from chromadb.api.types import EmbeddingFunction, Documents

    class _ChromaEmbeddingAdapter(EmbeddingFunction):
        def __call__(self, input: Documents) -> list:
            # chromadb 会分批调用，input 是文本列表
            try:
                loop = asyncio.get_event_loop()
            except RuntimeError:
                loop = None

            if loop and loop.is_running():
                # 已有事件循环（如 FastAPI 环境）, 用 run_coroutine_threadsafe
                import threading
                future = asyncio.run_coroutine_threadsafe(
                    embedding.embed(list(input)), loop
                )
                return future.result()
            else:
                # 无事件循环, 直接 run
                return asyncio.run(embedding.embed(list(input)))

        def __repr__(self) -> str:
            return f"_ChromaEmbeddingAdapter({embedding.name})"

    return _ChromaEmbeddingAdapter()


# ============================================================
# KnowledgeBase 对外 API
# ============================================================


class KnowledgeBase:
    """
    知识库 — 文档管理 + 语义检索

    基于 chromadb（ONNX embedding 模型），纯 Python 运行。
    数据持久化到 data/chroma_db/ 目录。

    用法：
        kb = KnowledgeBase()
        kb.add_document("AI 概述", "人工智能是...", user_id=1)
        results = kb.search("什么是 AI", top_k=3)
        kb.list_documents()
        kb.delete_document("doc_id")
    """

    def __init__(
        self,
        embedding: Optional[BaseEmbedding] = None,
        splitter: Optional[BaseSplitter] = None,
        hybrid_search: Optional[HybridSearch] = None,
        bm25_indexer: Optional[BM25Indexer] = None,
    ):
        self._embedding = embedding or ONNXEmbedding()
        self._splitter = splitter or RecursiveSplitter(chunk_size=CHUNK_SIZE, chunk_overlap=CHUNK_OVERLAP)
        self._hybrid_search = hybrid_search
        self._bm25_indexer = bm25_indexer

        self._client = chromadb.PersistentClient(path=str(CHROMA_DIR))

        # 尝试获取已有 collection，不存在则新建
        try:
            self._collection = self._client.get_collection(name="knowledge_base")
            logger.info(f"使用已有 collection (知识库)")
        except ValueError:
            self._ef = _create_chromadb_ef(self._embedding)
            self._collection = self._client.create_collection(
                name="knowledge_base",
                embedding_function=self._ef,
                metadata={"hnsw:space": "cosine"},
            )
            logger.info(f"新建 collection, 嵌入后端: {self._embedding.name}")

        # 加载文档注册表
        self._docs = _load_docs()

        logger.info(f"📚 知识库已加载: {len(self._docs)} 篇文档, "
                     f"{self._collection.count()} 个向量片段"
                     + (f", 混合搜索已启用" if self._hybrid_search else ""))

    @property
    def available(self) -> bool:
        """知识库始终可用"""
        try:
            self._collection.count()
            return True
        except Exception:
            return False

    @property
    def doc_count(self) -> int:
        return len(self._docs)

    @property
    def chunk_count(self) -> int:
        try:
            return self._collection.count()
        except Exception:
            return 0

    # ---------- 增删查 ----------

    def add_document(self, title: str, content: str, user_id: int = 0) -> dict:
        """
        添加文档：分块 → chromadb 自动 embedding → 持久化

        参数：
            title: 文档标题
            content: 文档正文（纯文本）
            user_id: 用户 ID（用于隔离不同用户的数据）

        返回：
            {"id": "xxx", "title": "xxx", "chunk_count": 5}
        """
        doc_id = uuid.uuid4().hex[:12]
        logger.info(f"[知识库] 1/5 生成文档 ID: {doc_id}")

        chunks = self._splitter.split_text(content)
        logger.info(f"[知识库] 2/5 分块完成: {len(content)} 字 → {len(chunks)} 块 (分块器: {self._splitter.name})")
        for i, c in enumerate(chunks):
            logger.info(f"       块[{i}]: {len(c)} 字 — {c[:40]}...")

        ids: list[str] = []
        texts: list[str] = []
        metadatas: list[dict] = []

        for i, text in enumerate(chunks):
            chunk_id = f"{doc_id}_{i}"
            ids.append(chunk_id)
            texts.append(text)
            metadatas.append({
                "doc_id": doc_id,
                "title": title,
                "user_id": user_id,
                "chunk_index": i,
            })

        logger.info(f"[知识库] 3/5 组装完成: {len(ids)} 个碎片, 准备写入 ChromaDB")

        self._collection.add(
            documents=texts,
            metadatas=metadatas,
            ids=ids,
        )
        logger.info(f"[知识库] 4/5 ChromaDB 写入完成 (自动 ONNX embedding → HNSW 索引)")

        # 更新文档注册表
        self._docs[doc_id] = {
            "title": title,
            "chunk_count": len(chunks),
            "user_id": user_id,
        }
        _save_docs(self._docs)

        # 构建 BM25 索引（如果启用了混合搜索）
        if self._bm25_indexer:
            try:
                chunk_texts = [{"chunk_id": ids[i], "text": texts[i]} for i in range(len(ids))]
                term_stats = self._bm25_indexer.get_term_stats_for_chunks(chunk_texts)
                self._bm25_indexer.add_documents(term_stats, doc_id=doc_id)
                logger.info(f"[知识库] BM25 索引更新完成: {len(term_stats)} 个块")
            except Exception as e:
                logger.warning(f"[知识库] BM25 索引更新失败: {e}")

        logger.info(f"[知识库] 5/5 注册表更新完成: {doc_id}.json")
        return {"id": doc_id, "title": title, "chunk_count": len(chunks)}

    def search(
        self,
        query: str,
        top_k: int = 3,
        user_id: Optional[int] = None,
        mode: str = "hybrid",
    ) -> list[dict]:
        """
        语义搜索，返回最相关的文档片段

        参数：
            query: 搜索关键词/问题
            top_k: 返回前 k 条
            user_id: 可选，按用户过滤
            mode: "hybrid"（混合搜索）| "dense"（仅稠密）| "sparse"（仅稀疏）

        返回：
            [{"content": "...", "title": "...", "doc_id": "...", "score": 0.85}, ...]
        """
        if not query.strip():
            return []

        where = None
        if user_id is not None:
            where = {"user_id": user_id}

        # 使用混合搜索（如果启用且 mode 是 hybrid 或 sparse）
        if self._hybrid_search and mode in ("hybrid", "sparse"):
            import asyncio
            import logging

            try:
                loop = asyncio.get_event_loop()
                if loop.is_running():
                    # 已在事件循环中
                    import threading
                    fut = asyncio.run_coroutine_threadsafe(
                        self._hybrid_search.search(
                            query=query,
                            top_k=top_k,
                            where=where,
                        ),
                        loop,
                    )
                    results = fut.result(timeout=10)
                else:
                    results = asyncio.run(self._hybrid_search.search(
                        query=query, top_k=top_k, where=where,
                    ))

                return [
                    {
                        "content": r.text,
                        "title": r.metadata.get("title", ""),
                        "doc_id": r.metadata.get("doc_id", r.chunk_id),
                        "score": r.score,
                    }
                    for r in results
                ]

            except Exception as e:
                logging.getLogger(__name__).warning(f"Hybrid search failed, falling back to dense: {e}")

        # 回退：纯稠密检索
        try:
            count = self._collection.count()
            results = self._collection.query(
                query_texts=[query],
                n_results=min(top_k, count or 1),
                where=where,
            )

            formatted = []
            if results and results.get("ids") and results["ids"][0]:
                for i in range(len(results["ids"][0])):
                    formatted.append({
                        "content": results["documents"][0][i],
                        "title": results["metadatas"][0][i].get("title", ""),
                        "doc_id": results["metadatas"][0][i].get("doc_id", ""),
                        "score": round(1 - results["distances"][0][i], 4),
                    })
            return formatted

        except Exception as e:
            logger.warning(f"知识库搜索异常: {e}")
            return []

    def list_documents(self) -> list[dict]:
        """列出所有文档"""
        return [
            {"id": k, "title": v["title"], "chunk_count": v["chunk_count"]}
            for k, v in self._docs.items()
        ]

    def delete_document(self, doc_id: str) -> bool:
        """删除文档及其所有向量片段"""
        if doc_id not in self._docs:
            return False

        # 找到该文档的所有 chunk ID
        try:
            # chromadb 支持按 metadata 批量删除
            self._collection.delete(where={"doc_id": doc_id})
        except Exception as e:
            logger.warning(f"删除 chromadb 片段失败: {e}")
            return False

        removed = self._docs.pop(doc_id, None)
        if removed:
            _save_docs(self._docs)
            logger.info(f"🗑️ 文档已删除: {doc_id} ({removed['chunk_count']} 个片段)")
            return True
        return False

    # ---------- BM25 索引 ----------

    def build_bm25_index(self) -> bool:
        """从 ChromaDB 现有数据重建 BM25 索引。

        Returns:
            是否成功。
        """
        if not self._bm25_indexer:
            logger.warning("BM25 indexer not configured")
            return False

        try:
            all_data = self._collection.get(include=["documents", "metadatas"])
            if not all_data or not all_data.get("ids"):
                logger.info("No data to build BM25 index")
                return False

            chunk_texts = []
            for i, cid in enumerate(all_data["ids"]):
                chunk_texts.append({
                    "chunk_id": cid,
                    "text": all_data["documents"][i],
                })

            term_stats = self._bm25_indexer.get_term_stats_for_chunks(chunk_texts)
            self._bm25_indexer.build(term_stats)
            logger.info(f"BM25 索引重建完成: {len(term_stats)} 个块, "
                         f"{len(self._bm25_indexer._index)} 个词项")
            return True

        except Exception as e:
            logger.warning(f"BM25 索引重建失败: {e}")
            return False

    # ---------- 分块 ----------

    @staticmethod
    def _chunk_text(text: str, chunk_size: int = CHUNK_SIZE,
                    overlap: int = CHUNK_OVERLAP) -> list[str]:
        """
        将长文本分割成重叠的块

        策略：
            - 优先在段落 (\\n\\n) 处切分
            - 其次在句子 (。！？.!?) 处切分
            - 最后按字符数硬切
        """
        if not text:
            return []

        text = text.strip()
        if len(text) <= chunk_size:
            return [text]

        chunks = []
        start = 0

        while start < len(text):
            if start + chunk_size >= len(text):
                chunks.append(text[start:])
                break

            candidate = text[start:start + chunk_size]

            # 优先在段落边界切
            para_break = candidate.rfind("\n\n")
            if para_break > chunk_size // 2:
                end = start + para_break
                chunks.append(text[start:end])
                start = end
                continue

            # 其次在句子边界切
            for sep in ("。", "！", "？", "！", ". ", "! ", "? "):
                last_sep = candidate.rfind(sep)
                if last_sep > chunk_size // 2:
                    end = start + last_sep + len(sep)
                    chunks.append(text[start:end])
                    start = end
                    break
            else:
                chunks.append(candidate)
                start = start + chunk_size

            start = start - overlap

        return chunks
