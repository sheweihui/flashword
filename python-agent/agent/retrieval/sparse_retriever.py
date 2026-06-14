"""稀疏检索器 — 基于 BM25 关键词的精确匹配检索。"""

from __future__ import annotations

import asyncio
from typing import Any, Dict, List, Optional

from .bm25_indexer import BM25Indexer
from .types import RetrievalResult


class SparseRetriever:
    """稀疏检索器。

    使用 BM25 算法，通过关键词匹配从倒排索引中检索相关文档。
    """

    def __init__(
        self,
        bm25_indexer: BM25Indexer,
        collection: Any,  # chromadb Collection
        collection_name: str = "default",
        top_k: int = 20,
    ):
        self._bm25 = bm25_indexer
        self._collection = collection
        self._collection_name = collection_name
        self.top_k = top_k

    async def retrieve(
        self,
        query_terms: List[str],
        top_k: Optional[int] = None,
    ) -> List[RetrievalResult]:
        """执行稀疏检索。

        Args:
            query_terms: 查询关键词列表。
            top_k: 返回数量。

        Returns:
            按 BM25 分数降序排列的检索结果。
        """
        k = top_k or self.top_k

        if not query_terms:
            return []

        try:
            # BM25 查询是同步的，用 asyncio.to_thread
            bm25_results = await asyncio.to_thread(
                self._bm25.query, query_terms, top_k=k,
            )

            if not bm25_results:
                return []

            # 通过 chunk_id 从 ChromaDB 获取文本和元数据
            chunk_ids = [r["chunk_id"] for r in bm25_results]
            id_to_score = {r["chunk_id"]: r["score"] for r in bm25_results}

            # 从 chromadb 批量获取
            try:
                db_results = await asyncio.to_thread(
                    self._collection.get,
                    ids=chunk_ids,
                    include=["documents", "metadatas"],
                )
            except Exception:
                db_results = {"ids": [], "documents": [], "metadatas": []}

            id_map = {}
            if db_results.get("ids"):
                for i, cid in enumerate(db_results["ids"]):
                    id_map[cid] = {
                        "text": db_results["documents"][i] if db_results.get("documents") else "",
                        "metadata": db_results["metadatas"][i] if db_results.get("metadatas") else {},
                    }

            results = []
            for cid in chunk_ids:
                if cid in id_map:
                    results.append(RetrievalResult(
                        chunk_id=cid,
                        score=round(id_to_score[cid], 4),
                        text=id_map[cid]["text"],
                        metadata=id_map[cid]["metadata"],
                    ))

            return results

        except Exception as e:
            import logging
            logging.getLogger(__name__).warning(f"Sparse retrieval failed: {e}")
            return []
