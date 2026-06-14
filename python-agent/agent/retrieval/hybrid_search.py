"""混合搜索编排器。

并行执行稠密 + 稀疏检索，通过 RRF 融合结果，支持优雅降级。
"""

from __future__ import annotations

import asyncio
import logging
from typing import Any, Dict, List, Optional

from .types import RetrievalResult, HybridSearchResult
from .dense_retriever import DenseRetriever
from .sparse_retriever import SparseRetriever
from .fusion import RRFFusion


logger = logging.getLogger(__name__)


class HybridSearch:
    """混合搜索编排器。

    流程:
        query → QueryProcessor(分词+关键词)
               ├─ DenseRetriever(query→embedding→chromadb)
               ├─ SparseRetriever(keywords→BM25→chromadb)
               └─ RRFFusion(dense结果 + sparse结果) → top_k
    """

    def __init__(
        self,
        dense_retriever: DenseRetriever,
        sparse_retriever: SparseRetriever,
        fusion: RRFFusion,
        dense_top_k: int = 20,
        sparse_top_k: int = 20,
        fusion_top_k: int = 10,
    ):
        self._dense = dense_retriever
        self._sparse = sparse_retriever
        self._fusion = fusion
        self.dense_top_k = dense_top_k
        self.sparse_top_k = sparse_top_k
        self.fusion_top_k = fusion_top_k

    async def search(
        self,
        query: str,
        query_terms: Optional[List[str]] = None,
        top_k: Optional[int] = None,
        where: Optional[Dict[str, Any]] = None,
        return_details: bool = False,
    ) -> List[RetrievalResult] | HybridSearchResult:
        """执行混合搜索。

        Args:
            query: 查询文本（用于稠密检索）。
            query_terms: 查询关键词（用于稀疏检索）。None 时自动从 query 提取。
            top_k: 最终返回数量。
            where: 元数据过滤条件。
            return_details: 是否返回包含明细的 HybridSearchResult。

        Returns:
            return_details=False: 融合后的结果列表。
            return_details=True: HybridSearchResult 包含各路明细。
        """
        k = top_k or self.fusion_top_k

        # 自动提取关键词（如果未提供）
        if query_terms is None:
            query_terms = self._extract_keywords(query)

        # 并行执行稠密和稀疏检索
        dense_task = self._dense.retrieve(query, top_k=self.dense_top_k, where=where)
        sparse_task = self._sparse.retrieve(query_terms, top_k=self.sparse_top_k)

        dense_results, sparse_results = await asyncio.gather(
            dense_task, sparse_task, return_exceptions=True,
        )

        # 处理异常
        if isinstance(dense_results, Exception):
            logger.warning(f"Dense retrieval failed: {dense_results}")
            dense_results = []
        if isinstance(sparse_results, Exception):
            logger.warning(f"Sparse retrieval failed: {sparse_results}")
            sparse_results = []

        # 判断是否降级
        used_fallback = False
        error = None

        if not dense_results and not sparse_results:
            error = "Both dense and sparse retrieval returned no results"
            return [] if not return_details else HybridSearchResult(
                results=[], dense_results=[], sparse_results=[],
                used_fallback=True, error=error,
            )

        if not dense_results:
            used_fallback = True
            logger.info("Dense retrieval empty, using sparse results only")
            final = sorted(sparse_results, key=lambda r: -r.score)[:k]
            return final if not return_details else HybridSearchResult(
                results=final, dense_results=[], sparse_results=sparse_results,
                used_fallback=True,
            )

        if not sparse_results:
            used_fallback = True
            logger.info("Sparse retrieval empty, using dense results only")
            final = sorted(dense_results, key=lambda r: -r.score)[:k]
            return final if not return_details else HybridSearchResult(
                results=final, dense_results=dense_results, sparse_results=[],
                used_fallback=True,
            )

        # 融合
        fused = self._fusion.fuse(
            [dense_results, sparse_results],
            top_k=k,
        )

        if return_details:
            return HybridSearchResult(
                results=fused,
                dense_results=dense_results,
                sparse_results=sparse_results,
                used_fallback=used_fallback,
            )

        return fused

    @staticmethod
    def _extract_keywords(text: str) -> List[str]:
        """从文本中提取关键词。

        使用 jieba 分词 + 简单停用词过滤。
        """
        import re
        import jieba

        _STOP_WORDS = {
            "the", "a", "an", "is", "are", "was", "were", "be", "been",
            "have", "has", "had", "do", "does", "did", "will", "would",
            "shall", "should", "may", "might", "can", "could", "must",
            "ought", "i", "you", "he", "she", "it", "we", "they",
            "my", "your", "his", "her", "its", "our", "their",
            "me", "him", "us", "them", "this", "that", "these", "those",
            "in", "on", "at", "to", "for", "with", "by", "from",
            "of", "and", "or", "not", "no", "but", "if", "so", "as",
            "than", "then", "also", "very", "just", "about",
        }

        text = text.lower()
        words = set()

        # 英文单词
        for w in re.findall(r"[a-z]+", text):
            if w not in _STOP_WORDS and len(w) > 1:
                words.add(w)

        # 中文分词
        for w in jieba.lcut(text):
            w = w.strip()
            if w and w not in _STOP_WORDS and len(w) > 1:
                words.add(w)

        return list(words)
