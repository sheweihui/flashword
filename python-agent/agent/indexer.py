"""
RAG 索引器：将原始数据转换为向量索引的 ETL 管道

完整链路：数据源萃取(Extract) → 转换分块(Transform) → 写入向量库(Load)

支持的数据源：
  - 用户上传文档 → KnowledgeBase（知识库集合）

职责边界：
  - 只做「写入」和「状态追踪」，不做检索
  - 检索走 RAGRetriever（agent/rag.py）
  - 单个文档的增删仍在 KnowledgeBase API 中
"""

import time
from dataclasses import dataclass, field
from typing import Optional

from loguru import logger


@dataclass
class IndexingStats:
    """单次或累计的索引统计"""
    documents: int = 0     # 文档数
    chunks: int = 0        # 分块/片段数
    errors: int = 0        # 失败数
    total_seconds: float = 0.0

    @property
    def items_total(self) -> int:
        return self.documents + self.chunks

    def merge(self, other: "IndexingStats") -> "IndexingStats":
        return IndexingStats(
            documents=self.documents + other.documents,
            chunks=self.chunks + other.chunks,
            errors=self.errors + other.errors,
            total_seconds=self.total_seconds + other.total_seconds,
        )

    def summary(self) -> str:
        parts = []
        if self.documents:
            parts.append(f"{self.documents} 文档")
        if self.chunks:
            parts.append(f"{self.chunks} 片段")
        avg_speed = f"{self.items_total / max(self.total_seconds, 0.01):.0f} item/s"
        return f"{', '.join(parts)} ({avg_speed}, {self.errors} 错误)"


class Indexer:
    """
    RAG 索引器

    将用户文档写入 KnowledgeBase 向量库。
    每次索引操作返回 IndexingStats，方便追踪和监控。

    用法：
        indexer = Indexer(kb=kb)
        stats = indexer.index_document(title, content, user_id=1)

    注意：
        indexer 不做重复检查 —— 多次索引同一份数据会产生重复向量。
        需要幂等场景请先调用 delete_xxx 再索引。
    """

    def __init__(self, kb=None, api=None):
        self.kb = kb   # KnowledgeBase 实例
        self.api = api # Endpoints 实例（可选）

        # 累计统计（从启动开始算）
        self.total_stats = IndexingStats()

    # ---------------------------------------------------------------
    # 文档索引 (→ KnowledgeBase)
    # ---------------------------------------------------------------

    def index_document(
        self,
        title: str,
        content: str,
        user_id: int = 0,
        source: str = "upload",
    ) -> IndexingStats:
        """单篇文档索引：分块 → 写入 KnowledgeBase"""
        if not self.kb:
            return IndexingStats(errors=1)

        t0 = time.time()
        try:
            result = self.kb.add_document(title, content, user_id)
            elapsed = time.time() - t0

            stats = IndexingStats(
                documents=1,
                chunks=result.get("chunk_count", 0),
                total_seconds=elapsed,
            )
            self.total_stats = self.total_stats.merge(stats)
            logger.info(f"索引文档 [{source}]: {title} ({stats.chunks} 块, {elapsed:.2f}s)")
            return stats

        except Exception as e:
            elapsed = time.time() - t0
            logger.error(f"索引文档失败 [{source}]: {title} — {e}")
            stats = IndexingStats(errors=1, total_seconds=elapsed)
            self.total_stats = self.total_stats.merge(stats)
            return stats

    def index_document_batch(
        self,
        documents: list[dict],
        user_id: int = 0,
        source: str = "batch",
    ) -> IndexingStats:
        """批量文档索引"""
        total = IndexingStats()
        for doc in documents:
            stats = self.index_document(
                title=doc.get("title", ""),
                content=doc.get("content", ""),
                user_id=user_id or doc.get("user_id", 0),
                source=source,
            )
            total = total.merge(stats)
        return total

    # ---------------------------------------------------------------
    # 索引管理与统计
    # ---------------------------------------------------------------

    def get_stats(self) -> dict:
        """返回索引统计（日志/监控用）"""
        return {
            "total_documents": self.total_stats.documents,
            "total_chunks": self.total_stats.chunks,
            "total_errors": self.total_stats.errors,
            "total_items": self.total_stats.items_total,
            "total_seconds": round(self.total_stats.total_seconds, 2),
        }

    def reset_stats(self):
        """重置累计统计"""
        self.total_stats = IndexingStats()
