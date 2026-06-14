"""稠密检索器 — 基于向量相似度的语义检索。"""

from __future__ import annotations

from typing import Any, Dict, List, Optional

from agent.embeddings import BaseEmbedding
from .types import RetrievalResult


class DenseRetriever:
    """稠密检索器。

    使用嵌入模型将查询转为向量，在向量库中做语义相似度搜索。
    """

    def __init__(
        self,
        embedding: BaseEmbedding,
        collection: Any,  # chromadb Collection
        top_k: int = 20,
    ):
        self._embedding = embedding
        self._collection = collection
        self.top_k = top_k

    async def retrieve(
        self,
        query: str,
        top_k: Optional[int] = None,
        where: Optional[Dict[str, Any]] = None,
    ) -> List[RetrievalResult]:
        """执行稠密检索。

        Args:
            query: 查询文本。
            top_k: 返回数量（默认 self.top_k）。
            where: 元数据过滤条件。

        Returns:
            按相似度降序排列的检索结果。
        """
        import asyncio

        k = top_k or self.top_k

        try:
            count = self._collection.count()
            if count == 0:
                return []

            # 异步嵌入查询
            query_vector = await self._embedding.embed([query])

            # ChromaDB 查询是同步的，用 asyncio.to_thread 包装
            results = await asyncio.to_thread(
                self._collection.query,
                query_embeddings=query_vector,
                n_results=min(k, count),
                where=where,
                include=["documents", "metadatas", "distances"],
            )

            formatted = []
            if results and results.get("ids") and results["ids"][0]:
                for i in range(len(results["ids"][0])):
                    score = 1.0 - (results["distances"][0][i] / 2.0)
                    formatted.append(RetrievalResult(
                        chunk_id=results["ids"][0][i],
                        score=round(score, 4),
                        text=results["documents"][0][i],
                        metadata=results["metadatas"][0][i],
                    ))
            return formatted

        except Exception as e:
            import logging
            logging.getLogger(__name__).warning(f"Dense retrieval failed: {e}")
            return []
