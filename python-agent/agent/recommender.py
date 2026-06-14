"""
学习资料推荐引擎 — 基于用户词本内容的向量匹配推荐

原理：
    1. 把商店所有词书的名称向量化，存入 chromadb
    2. 获取用户已有的词书名称 → 向量化 → 作为"用户兴趣向量"
    3. 用余弦相似度找最匹配的商店词书

数据流：
    api.get_store_books() → 词书列表 → 向量化 → chromadb 缓存
    api.get_book_list(user) → 用户词书 → 向量化 → 匹配推荐
"""

from typing import Optional

from loguru import logger

from api.endpoints import Endpoints

import chromadb
from chromadb.utils import embedding_functions

# chromadb 持久化路径（复用主知识库的目录）
from agent.knowledge_base import CHROMA_DIR


class RecommendEngine:
    """
    学习资料推荐引擎

    用法：
        engine = RecommendEngine(api_endpoints)
        results = engine.recommend(user_id=1, top_k=5)
        # → [{"book_name": "考研词汇", "score": 0.85, "word_count": 3000}, ...]
    """

    # 商店词书名称索引的 chromadb 集合名
    _COLLECTION_NAME = "store_books"
    # 商店词书内容（单词列表）的 chromadb 集合名
    _WORD_CONTENT_COLLECTION = "book_word_content"

    def __init__(self, api: Endpoints):
        self.api = api
        self._client = chromadb.PersistentClient(path=str(CHROMA_DIR))
        self._ef = embedding_functions.DefaultEmbeddingFunction()
        self._collection = self._client.get_or_create_collection(
            name=self._COLLECTION_NAME,
            embedding_function=self._ef,
            metadata={"hnsw:space": "cosine"},
        )

    # ---------------------------------------------------------------
    # 公开方法
    # ---------------------------------------------------------------

    def recommend(self, user_id: int, top_k: int = 5) -> list[dict]:
        """
        为用户推荐词书

        返回按相似度降序排列：
            [{"book_name": "...", "book_id": int, "word_count": int,
              "source_type": int, "score": float}, ...]
        """
        # 1. 确保商店词书已索引
        store_books = self._ensure_indexed()

        if not store_books:
            logger.warning("商店无词书可推荐")
            return []

        # 2. 获取用户已有词书 → 构建兴趣文本
        interest_text = self._build_interest_text(user_id)
        if not interest_text:
            logger.info(f"用户 {user_id} 无词书数据，返回热门推荐")
            return self._hot_recommendations(store_books, top_k)

        logger.debug(f"用户兴趣文本: {interest_text[:100]}...")

        # 3. 用兴趣文本向量搜索 chromadb
        try:
            n_results = min(top_k * 2, self._collection.count())
            results = self._collection.query(
                query_texts=[interest_text],
                n_results=n_results,
            )
        except Exception as e:
            logger.warning(f"推荐查询异常: {e}")
            return self._hot_recommendations(store_books, top_k)

        # 4. 格式化结果，排除用户已拥有的词书
        user_book_ids = self._get_user_book_ids(user_id)
        recommendations = []
        if results and results.get("ids") and results["ids"][0]:
            for i in range(len(results["ids"][0])):
                meta = results["metadatas"][0][i]
                book_id = meta.get("book_id")
                if book_id in user_book_ids:
                    continue  # 跳过已拥有的
                recommendations.append({
                    "book_name": meta.get("book_name", ""),
                    "book_id": book_id,
                    "word_count": meta.get("word_count", 0),
                    "source_type": meta.get("source_type", 0),
                    "score": round(1 - results["distances"][0][i], 4),
                })

        # 5. 如果推荐不足 top_k，补热门词书
        if len(recommendations) < top_k:
            existing_ids = {r["book_id"] for r in recommendations} | user_book_ids
            for book in store_books:
                if book["id"] not in existing_ids:
                    recommendations.append({
                        "book_name": book["book_name"],
                        "book_id": book["id"],
                        "word_count": book["word_count"],
                        "source_type": book["source_type"],
                        "score": 0.0,
                    })
                    if len(recommendations) >= top_k:
                        break

        return recommendations[:top_k]

    # ---------------------------------------------------------------
    # 商店词书索引
    # ---------------------------------------------------------------

    def _ensure_indexed(self) -> list[dict]:
        """确保商店词书已向量化到 chromadb，返回完整词书列表"""
        # 尝试从 chromadb 读取已有索引
        try:
            count = self._collection.count()
        except Exception:
            count = 0

        if count > 0:
            # 已有缓存，从 chromadb metadata 恢复列表
            return self._load_books_from_index()

        # 首次：从后端拉取全部词书
        return self._index_all_books()

    def _index_all_books(self) -> list[dict]:
        """从后端接口拉取商店词书并建立向量索引"""
        all_books = []
        page = 1
        while True:
            try:
                resp = self.api.get_store_books(page=page, size=50)
            except Exception as e:
                logger.warning(f"拉取商品词书第 {page} 页失败: {e}")
                break

            if not resp:
                break

            # 兼容可能的数据结构
            books = resp if isinstance(resp, list) else resp.get("records", [])

            if not books:
                break

            all_books.extend(books)
            page += 1

        if not all_books:
            return []

        # 写入 chromadb
        ids: list[str] = []
        texts: list[str] = []
        metadatas: list[dict] = []

        for book in all_books:
            book_id = book.get("id") or book.get("bookId") or 0
            book_name = book.get("bookName") or book.get("name") or ""
            word_count = book.get("wordCount") or book.get("word_count") or 0
            source_type = book.get("sourceType") or book.get("source_type") or 0

            ids.append(str(book_id))
            texts.append(book_name)
            metadatas.append({
                "book_id": book_id,
                "book_name": book_name,
                "word_count": word_count,
                "source_type": source_type,
            })

        # chromadb 要求 id 不重复，先清空再写
        try:
            self._collection.delete(where={"book_id": {"$gte": 0}})
        except Exception:
            pass  # 初次无数据时忽略

        self._collection.add(documents=texts, metadatas=metadatas, ids=ids)
        logger.info(f"商店词书索引完成: {len(all_books)} 本")

        return [
            {"id": m["book_id"], "book_name": m["book_name"],
             "word_count": m["word_count"], "source_type": m["source_type"]}
            for m in metadatas
        ]

    def _load_books_from_index(self) -> list[dict]:
        """从 chromadb 恢复词书列表"""
        try:
            all_data = self._collection.get()
        except Exception:
            return []

        books = []
        if all_data and all_data.get("metadatas"):
            for meta in all_data["metadatas"]:
                books.append({
                    "id": meta.get("book_id", 0),
                    "book_name": meta.get("book_name", ""),
                    "word_count": meta.get("word_count", 0),
                    "source_type": meta.get("source_type", 0),
                })
        return books

    # ---------------------------------------------------------------
    # 用户画像
    # ---------------------------------------------------------------

    def _build_interest_text(self, user_id: int) -> str:
        """
        从用户的现有词书构建兴趣文本

        用户有《四级词汇》《六级词汇》→ 返回 "四级词汇 六级词汇"
        然后被向量化，跟商店词书做语义匹配。
        """
        try:
            books = self.api.get_book_list(user_id)
        except Exception as e:
            logger.debug(f"获取用户 {user_id} 词书失败: {e}")
            return ""

        if not books:
            return ""

        book_names = []
        for book in books[:10]:
            name = book.get("bookName") or book.get("name") or ""
            if name:
                book_names.append(name)

        return " ".join(book_names) if book_names else ""

    def _get_user_book_ids(self, user_id: int) -> set[int]:
        """获取用户已拥有的词书 ID 集合，用于排除"""
        try:
            books = self.api.get_book_list(user_id)
        except Exception:
            return set()

        return {b.get("id", 0) for b in (books or [])}

    # ---------------------------------------------------------------
    # 基于词书内容的语义推荐（RAG 用）
    # ---------------------------------------------------------------

    def recommend_by_word_content(self, query_words: list[str], top_k: int = 3) -> list[dict]:
        """
        根据用户提到的单词，搜索商店词书中包含这些单词（或语义相近单词）的词书。

        两层策略：
            第一层：词书内容索引（每个词书的所有单词 → chromadb 向量化）
                    如果索引不存在，自动触发懒加载建索引
            第二层（兜底）：如果内容索引不可用或结果不足，用词书名搜索替代
        """
        if not query_words:
            return []

        query_text = " ".join(query_words)

        # 第一层：词书内容语义搜索
        try:
            if self._ensure_word_content_indexed():
                col = self._get_or_create_word_content_collection()
                n_results = min(top_k * 2, max(col.count(), 1))
                results = col.query(query_texts=[query_text], n_results=n_results)

                recommendations = []
                if results and results.get("ids") and results["ids"][0]:
                    for i in range(len(results["ids"][0])):
                        meta = results["metadatas"][0][i]
                        recommendations.append({
                            "book_name": meta.get("book_name", ""),
                            "book_id": meta.get("book_id", 0),
                            "word_count": meta.get("word_count", 0),
                            "score": round(1 - results["distances"][0][i], 4),
                        })

                if recommendations:
                    return recommendations[:top_k]

                logger.debug("词书内容索引无匹配，降级到书名搜索")
        except Exception as e:
            logger.debug(f"词书内容搜索失败，降级到书名搜索: {e}")

        # 第二层（兜底）：书名搜索
        return self._recommend_by_book_name(query_text, top_k)

    def _recommend_by_book_name(self, query_text: str, top_k: int) -> list[dict]:
        """兜底方案：用书名做语义匹配"""
        store_books = self._ensure_indexed()
        if not store_books:
            return []

        try:
            n_results = min(top_k * 2, self._collection.count())
            results = self._collection.query(
                query_texts=[query_text],
                n_results=n_results,
            )
        except Exception as e:
            logger.warning(f"书名搜索异常: {e}")
            return []

        recommendations = []
        if results and results.get("ids") and results["ids"][0]:
            for i in range(len(results["ids"][0])):
                meta = results["metadatas"][0][i]
                recommendations.append({
                    "book_name": meta.get("book_name", ""),
                    "book_id": meta.get("book_id", 0),
                    "word_count": meta.get("word_count", 0),
                    "score": round(1 - results["distances"][0][i], 4),
                })

        return recommendations[:top_k]

    def _ensure_word_content_indexed(self) -> bool:
        """确保词书内容索引已建立（每个词书的单词列表 → chromadb 向量化）

        首次调用时：
            1. 获取商店所有词书列表
            2. 对每本词书，调用 get_words_by_book() 获取单词列表
            3. 所有单词合并为文档，写入 chromadb "book_word_content" 集合
            4. chromadb 持久化到磁盘，下次启动无需重建

        返回 True 表示索引可用。
        """
        col = self._get_or_create_word_content_collection()
        try:
            if col.count() > 0:
                return True
        except Exception:
            pass

        # 获取所有商店词书
        store_books = self._ensure_indexed()
        if not store_books:
            logger.warning("没有商店词书可索引")
            return False

        logger.info(f"开始索引词书内容: {len(store_books)} 本词书（首次可能较慢）")

        ids: list[str] = []
        texts: list[str] = []
        metadatas: list[dict] = []
        auth_failure = False

        for book in store_books:
            if auth_failure:
                break

            book_id = book.get("id") or book.get("book_id")
            book_name = book.get("book_name", "")

            if not book_id:
                continue

            try:
                # 获取这本词书的所有单词
                words_data = self.api.get_words_by_book(book_id)
                if not words_data or not isinstance(words_data, list):
                    continue

                # 提取单词文本
                word_texts = [
                    w.get("wordText", "") for w in words_data
                    if w.get("wordText")
                ]
                if not word_texts:
                    continue

                ids.append(str(book_id))
                # 所有单词用空格连接成一个文档
                texts.append(" ".join(word_texts))
                metadatas.append({
                    "book_id": book_id,
                    "book_name": book_name,
                    "word_count": len(word_texts),
                })
                logger.debug(f"  已索引 [{book_id}] {book_name}: {len(word_texts)} 词")

            except Exception as e:
                err = str(e).lower()
                if "auth" in err or "login" in err or "token" in err or "unauthorized" in err or "401" in err:
                    logger.warning(f"认证失败，停止词书内容索引（后续需要登录后重试）")
                    auth_failure = True
                else:
                    logger.debug(f"  跳过词书 [{book_id}] {book_name}: {e}")
                continue

        if not texts:
            logger.warning("没有词书内容可索引")
            return False

        # 清空旧数据，写入新数据
        try:
            col.delete(where={"book_id": {"$gte": 0}})
        except Exception:
            pass

        col.add(documents=texts, metadatas=metadatas, ids=ids)
        logger.info(f"词书内容索引完成: {len(texts)} 本词书, {sum(m['word_count'] for m in metadatas)} 个单词")
        return True

    def _get_or_create_word_content_collection(self):
        """获取或创建词书内容的 chromadb 集合"""
        return self._client.get_or_create_collection(
            name=self._WORD_CONTENT_COLLECTION,
            embedding_function=self._ef,
            metadata={"hnsw:space": "cosine"},
        )

    # ---------------------------------------------------------------
    # 兜底：热门推荐
    # ---------------------------------------------------------------

    @staticmethod
    def _hot_recommendations(store_books: list[dict], top_k: int) -> list[dict]:
        """用户无数据时按词书词汇量降序排列当热门推荐"""
        sorted_books = sorted(
            store_books,
            key=lambda b: b.get("word_count", 0),
            reverse=True,
        )
        return [
            {
                "book_name": b["book_name"],
                "book_id": b["id"],
                "word_count": b["word_count"],
                "source_type": b["source_type"],
                "score": 0.0,
            }
            for b in sorted_books[:top_k]
        ]
