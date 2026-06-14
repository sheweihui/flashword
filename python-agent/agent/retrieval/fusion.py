"""RRF（Reciprocal Rank Fusion）融合算法。

将多路检索结果基于排名而非分数进行融合。
"""

from __future__ import annotations

from typing import Dict, List, Optional

from .types import RetrievalResult


class RRFFusion:
    """RRF 融合。

    公式: RRF_score(d) = Σ 1 / (k + rank(d))

    k=60 是论文推荐值。
    """

    def __init__(self, k: int = 60):
        self.k = k

    def fuse(
        self,
        ranking_lists: List[List[RetrievalResult]],
        top_k: Optional[int] = None,
    ) -> List[RetrievalResult]:
        """融合多路检索结果。

        Args:
            ranking_lists: 多路检索结果列表，每路按分数降序排列。
            top_k: 融合后返回的数量。

        Returns:
            按 RRF 分数降序排列的融合结果。
        """
        if not ranking_lists:
            return []

        non_empty = [lst for lst in ranking_lists if lst]
        if not non_empty:
            return []

        # 计算 RRF 分数
        rrf_scores: Dict[str, float] = {}
        chunk_data: Dict[str, RetrievalResult] = {}

        for ranking in non_empty:
            for rank, result in enumerate(ranking, start=1):
                cid = result.chunk_id
                contribution = 1.0 / (self.k + rank)
                if cid not in rrf_scores:
                    rrf_scores[cid] = 0.0
                    chunk_data[cid] = result
                rrf_scores[cid] += contribution

        # 构建结果
        fused = [
            RetrievalResult(
                chunk_id=cid,
                score=round(score, 4),
                text=chunk_data[cid].text,
                metadata=chunk_data[cid].metadata.copy(),
            )
            for cid, score in rrf_scores.items()
        ]

        fused.sort(key=lambda r: (-r.score, r.chunk_id))

        if top_k is not None and top_k > 0:
            fused = fused[:top_k]

        return fused
