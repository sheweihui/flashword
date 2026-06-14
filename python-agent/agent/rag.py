"""RAG 检索模块：从后端拉取用户数据作为上下文"""

import asyncio
import re
import time
from typing import Optional
from loguru import logger

import redis.asyncio as aioredis

from api.endpoints import Endpoints
from config.settings import REDIS_HOST, REDIS_PORT, REDIS_DB, REDIS_PASSWORD
from agent.trace import TraceContext

# 知识库（可选注入，由 server.py 传入）
try:
    from agent.knowledge_base import KnowledgeBase
    HAS_KB = True
except ImportError:
    HAS_KB = False
    KnowledgeBase = None

# 英语停用词（模块级常量）
_STOPWORDS: set[str] = {
    "the", "is", "are", "was", "were", "has", "have", "had", "do",
    "does", "did", "will", "would", "could", "should", "may", "might",
    "can", "shall", "am", "be", "been", "being", "it", "its", "this",
    "that", "these", "those", "what", "which", "who", "whom", "whose",
    "when", "where", "why", "how", "a", "an", "and", "or", "but", "if",
    "because", "so", "than", "too", "very", "just", "about", "above",
    "after", "again", "all", "also", "any", "back", "each", "every",
    "for", "from", "get", "got", "here", "him", "his", "into", "let",
    "like", "make", "more", "most", "much", "must", "my", "no", "not",
    "now", "of", "on", "one", "only", "other", "our", "out", "over",
    "own", "say", "she", "some", "tell", "their", "them", "then",
    "there", "they", "thing", "things", "think", "through", "upon",
    "use", "used", "way", "well", "with", "without", "you", "your",
    "want", "know", "help", "test", "learn", "study", "recommend",
    "need", "give", "tell", "show", "check", "see", "look",
}

# RAG 缓存配置
_RAG_CACHE_DEFAULT_TTL = 60  # 默认过期时间（秒）

_REDIS_KEY_PREFIX = "rag:"


class RedisCache:
    """基于 Redis 的缓存，接口兼容 _LRUCache"""

    def __init__(self, default_ttl: int = _RAG_CACHE_DEFAULT_TTL):
        self._default_ttl = default_ttl
        self._redis: Optional[aioredis.Redis] = None

    async def _get_conn(self) -> aioredis.Redis:
        if self._redis is None:
            self._redis = aioredis.Redis(
                host=REDIS_HOST,
                port=REDIS_PORT,
                db=REDIS_DB,
                password=REDIS_PASSWORD or None,
                decode_responses=True,
            )
        return self._redis

    async def get(self, key: str) -> Optional[str]:
        r = await self._get_conn()
        value = await r.get(f"{_REDIS_KEY_PREFIX}{key}")
        return value

    async def set(self, key: str, value: str, ttl: Optional[int] = None):
        r = await self._get_conn()
        await r.setex(
            f"{_REDIS_KEY_PREFIX}{key}",
            ttl if ttl is not None else self._default_ttl,
            value,
        )

    async def clear(self, key: Optional[str] = None):
        r = await self._get_conn()
        if key:
            await r.delete(f"{_REDIS_KEY_PREFIX}{key}")
        else:
            # 清空所有 rag: 前缀的 key
            cursor = 0
            while True:
                cursor, keys = await r.scan(cursor, match=f"{_REDIS_KEY_PREFIX}*")
                if keys:
                    await r.delete(*keys)
                if cursor == 0:
                    break

    async def close(self):
        if self._redis:
            await self._redis.aclose()
            self._redis = None

    @property
    async def size(self) -> int:
        r = await self._get_conn()
        cursor, keys = await r.scan(0, match=f"{_REDIS_KEY_PREFIX}*")
        return len(keys)


class RAGRetriever:
    """从后端检索与用户问题相关的上下文"""

    def __init__(self, api: Endpoints, kb: 'KnowledgeBase | None' = None):
        self.api = api
        self.kb = kb if HAS_KB else None
        self._cache = RedisCache()

    async def clear_cache(self, key: Optional[str] = None):
        await self._cache.clear(key)

    # ---- 主入口 ----

    async def retrieve_context(
        self,
        user_id: Optional[int],
        message: str,
        trace: Optional[TraceContext] = None,
    ) -> str:
        """
        根据用户问题和身份检索相关上下文。

        并发执行所有独立查询：公共查词、个人查词、用户概况。
        返回拼装后的 Markdown 文本。

        Args:
            user_id: 用户 ID。
            message: 用户消息。
            trace: 可选的 TraceContext 用于记录追踪数据。
        """
        words = self._extract_words(message)
        logger.info(f"[RAG] 提取关键词: 消息=\"{message[:50]}...\" → {words}")

        tasks: list[asyncio.Task] = []
        task_names: list[str] = []

        if words:
            for word in words[:3]:
                tasks.append(asyncio.create_task(self._search_public_word(word)))
                task_names.append(f"查词:{word}")
                if user_id:
                    tasks.append(asyncio.create_task(self._search_my_word(user_id, word)))
                    task_names.append(f"个人词本:{word}")

        if user_id:
            tasks.append(asyncio.create_task(self._get_user_profile(user_id)))
            task_names.append("用户概况")
            if self.kb:
                tasks.append(asyncio.create_task(self._search_knowledge_base(user_id, message)))
                task_names.append("知识库搜索")

        if not tasks:
            if trace:
                trace.record_stage("retrieve_context", 0, data={"message": message[:50], "tasks": 0})
            return ""

        t0 = time.time()
        results = await asyncio.gather(*tasks, return_exceptions=True)
        elapsed = (time.time() - t0) * 1000

        parts: list[str] = []
        success = 0
        failed = 0
        task_details = []
        for name, r in zip(task_names, results):
            if isinstance(r, Exception):
                failed += 1
                task_details.append({"task": name, "status": "failed", "error": str(r)[:100]})
                logger.info(f"[RAG]   {name} 异常: {type(r).__name__}: {r}")
                continue
            if r and isinstance(r, str) and r.strip():
                success += 1
                parts.append(r)
                task_details.append({"task": name, "status": "success", "chars": len(r)})
            else:
                task_details.append({"task": name, "status": "empty"})

        logger.info(f"[RAG] 结果汇总: {success} 成功, {failed} 失败 ({elapsed:.0f}ms)")

        if trace:
            trace.set_metadata("word_count", len(words))
            trace.record_stage("retrieve_context", elapsed, data={
                "tasks": task_details,
                "success": success,
                "failed": failed,
                "message_preview": message[:80],
            })

        # 按标题去重
        seen: set[str] = set()
        deduped: list[str] = []
        for p in parts:
            key = next(
                (line for line in p.split("\n") if line.startswith("## ")),
                p[:60],
            )
            if key not in seen:
                seen.add(key)
                deduped.append(p)

        context = "\n\n".join(deduped)
        logger.info(f"[RAG] 最终上下文: {len(context)} 字符, {len(deduped)} 个片段")
        return context

    # ---- 子任务 ----

    async def _search_public_word(self, word: str) -> Optional[str]:
        cache_key = f"word:{word}"
        cached = await self._cache.get(cache_key)
        if cached:
            return cached

        try:
            result = await asyncio.to_thread(self.api.search_word, word)
            word_info = self._format_word_context(result)
            if word_info:
                section = f"## 单词查询\n{word_info}"
                await self._cache.set(cache_key, section, ttl=120)
                return section
        except Exception as e:
            logger.debug(f"查词失败 {word}: {e}")

        return None

    async def _search_my_word(self, user_id: int, word: str) -> Optional[str]:
        cache_key = f"myword:{user_id}:{word}"
        cached = await self._cache.get(cache_key)
        if cached:
            return cached

        try:
            result = await asyncio.to_thread(self.api.search_my_word, word)
            if result and isinstance(result, list) and result:
                my_info = self._format_my_word_context(result)
                if my_info:
                    section = f"## 我的单词本\n{my_info}"
                    await self._cache.set(cache_key, section, ttl=120)
                    return section
        except Exception as e:
            logger.debug(f"搜索个人单词失败 {word}: {e}")

        return None

    async def _get_user_profile(self, user_id: int) -> Optional[str]:
        cache_key = f"profile:{user_id}"
        cached = await self._cache.get(cache_key)
        if cached:
            return cached

        balance_task = asyncio.to_thread(self.api.get_points_balance)
        info_task = asyncio.to_thread(self.api.get_user_info, user_id)
        books_task = asyncio.to_thread(self.api.get_book_list, user_id)

        balance, info, books = await asyncio.gather(
            balance_task, info_task, books_task,
            return_exceptions=True,
        )

        lines: list[str] = []

        if isinstance(balance, dict) and balance.get("balance") is not None:
            lines.append(f"- 积分余额: {balance['balance']}")

        if isinstance(info, dict):
            nickname = info.get("nickname") or info.get("username", "")
            if nickname:
                lines.append(f"- 用户名: {nickname}")

        if isinstance(books, list) and books:
            lines.append(f"- 单词本数量: {len(books)}")

            preview_tasks = []
            valid_books = []
            for book in books[:5]:
                book_id = book.get("id")
                word_count = book.get("wordCount", 0)
                if book_id and word_count and word_count > 0:
                    preview_tasks.append(
                        asyncio.to_thread(self.api.get_words_by_book, book_id)
                    )
                    valid_books.append(book)
                else:
                    valid_books.append(book)
                    preview_tasks.append(None)

            if preview_tasks:
                word_results = await asyncio.gather(
                    *[t for t in preview_tasks if t is not None],
                    return_exceptions=True,
                )
            else:
                word_results = []

            word_idx = 0
            for i, book in enumerate(valid_books):
                book_name = book.get("bookName", "?")
                word_count = book.get("wordCount", 0)
                source = "购买" if book.get("sourceType") == 2 else "自建"
                lines.append(f"  - [{book.get('id')}] 《{book_name}》({source}) — {word_count} 词")

                if preview_tasks[i] is not None and word_idx < len(word_results):
                    r = word_results[word_idx]
                    word_idx += 1
                    if isinstance(r, list) and r:
                        previews = [w.get("wordText", "?") for w in r[:5]]
                        lines.append(f"    包含: {', '.join(previews)}{'...' if len(r) > 5 else ''}")

        if not lines:
            lines.append(f"- 用户ID: {user_id}")

        section = f"## 用户学习概况\n" + "\n".join(lines)
        await self._cache.set(cache_key, section)
        return section

    # ---- 知识库搜索（向量语义检索） ----

    async def _search_knowledge_base(self, user_id: int, message: str) -> Optional[str]:
        """从用户的知识库中搜索与问题相关的文档片段"""
        if not self.kb:
            return None

        try:
            logger.info(f"[RAG-知识库] 搜索: query=\"{message[:40]}...\", user_id={user_id}")
            results = self.kb.search(message, top_k=3, user_id=user_id)
            if results:
                items = []
                for r in results:
                    preview = r["content"][:150].replace("\n", " ")
                    items.append(f"- [{r['title']}](相似度: {r['score']:.2f}) {preview}...")
                section = "## 知识库匹配\n" + "\n".join(items)
                logger.debug(f"知识库命中 {len(results)} 条")
                return section
        except Exception as e:
            logger.debug(f"知识库搜索异常: {e}")

        return None

    # ---- 格式化 ----

    def _format_my_word_context(self, results: list) -> str:
        lines = []
        for w in results[:5]:
            text = w.get("wordText", "?")
            definition = w.get("definition", "")
            tags = w.get("tags", "")
            note = w.get("note", "")
            parts = [f"- {text}"]
            if definition:
                parts.append(f"释义: {definition}")
            if tags:
                parts.append(f"标签: {tags}")
            if note:
                parts.append(f"笔记: {note}")
            lines.append(" | ".join(parts))
        return "\n".join(lines) if lines else ""

    def _format_word_context(self, result) -> str:
        if isinstance(result, list) and result:
            w = result[0]
        elif isinstance(result, dict):
            w = result
        else:
            return ""

        fields = []
        if w.get("wordText"):
            fields.append(f"单词: {w['wordText']}")
        if w.get("phonetic"):
            fields.append(f"音标: {w['phonetic']}")
        if w.get("partOfSpeech"):
            fields.append(f"词性: {w['partOfSpeech']}")
        if w.get("definition"):
            fields.append(f"释义: {w['definition']}")
        if w.get("exampleSentence"):
            fields.append(f"例句: {w['exampleSentence']}")
        if w.get("exampleTranslation"):
            fields.append(f"翻译: {w['exampleTranslation']}")

        return " | ".join(fields) if fields else ""

    @staticmethod
    def _extract_words(text: str) -> list[str]:
        # 中文和英文混排时 \b 不生效（中文字符也被 Python 视为 \w）
        # 所以先用 [^a-zA-Z] 切分，再匹配纯英文词
        parts = re.split(r"[^a-zA-Z]", text)
        candidates = [p for p in parts if 2 <= len(p) <= 20]
        return [w for w in candidates if w.lower() not in _STOPWORDS]
