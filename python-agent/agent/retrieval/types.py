"""检索相关的数据类。"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional


@dataclass
class RetrievalResult:
    """单个检索结果。"""
    chunk_id: str
    score: float
    text: str
    metadata: Dict[str, Any] = field(default_factory=dict)


@dataclass
class HybridSearchResult:
    """混合搜索的完整结果。"""
    results: List[RetrievalResult]
    dense_results: List[RetrievalResult]
    sparse_results: List[RetrievalResult]
    used_fallback: bool = False
    error: Optional[str] = None
