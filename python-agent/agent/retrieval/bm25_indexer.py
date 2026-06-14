"""BM25 倒排索引实现。

来自 Modular RAG MCP Server 的 BM25Indexer，适配异步场景。
"""

from __future__ import annotations

import json
import math
from pathlib import Path
from typing import Any, Dict, List, Optional

from .types import RetrievalResult


class BM25Indexer:
    """BM25 倒排索引。

    支持构建、查询、增量更新和持久化。
    """

    def __init__(
        self,
        index_dir: str = "data/bm25",
        k1: float = 1.5,
        b: float = 0.75,
    ):
        self.index_dir = Path(index_dir)
        self.k1 = k1
        self.b = b
        self._index: Dict[str, Dict[str, Any]] = {}
        self._metadata: Dict[str, Any] = {}

    def build(
        self,
        term_stats: List[Dict[str, Any]],
        collection: str = "default",
    ) -> None:
        """从词频统计构建 BM25 索引。"""
        if not term_stats:
            raise ValueError("Cannot build index from empty term_stats")

        num_docs = len(term_stats)
        total_length = sum(stat["doc_length"] for stat in term_stats)
        avg_doc_length = total_length / num_docs if num_docs > 0 else 0.0

        # 计算文档频率
        doc_freq: Dict[str, int] = {}
        for stat in term_stats:
            for term in stat["term_frequencies"].keys():
                doc_freq[term] = doc_freq.get(term, 0) + 1

        # 构建倒排索引
        index: Dict[str, Dict[str, Any]] = {}
        for term, df in doc_freq.items():
            idf = self._calculate_idf(num_docs, df)
            postings = []
            for stat in term_stats:
                tf = stat["term_frequencies"].get(term, 0)
                if tf > 0:
                    postings.append({
                        "chunk_id": stat["chunk_id"],
                        "tf": tf,
                        "doc_length": stat["doc_length"],
                    })
            index[term] = {"idf": idf, "df": df, "postings": postings}

        self._metadata = {
            "num_docs": num_docs,
            "avg_doc_length": avg_doc_length,
            "total_terms": len(index),
            "collection": collection,
        }
        self._index = index
        self._save(collection)

    def load(self, collection: str = "default") -> bool:
        """从磁盘加载索引。"""
        index_path = self._get_index_path(collection)
        if not index_path.exists():
            return False
        with open(index_path, "r", encoding="utf-8") as f:
            data = json.load(f)
        if "metadata" not in data or "index" not in data:
            raise ValueError("Invalid index file structure")
        self._metadata = data["metadata"]
        self._index = data["index"]
        return True

    def query(
        self,
        query_terms: List[str],
        top_k: int = 10,
    ) -> List[Dict[str, Any]]:
        """查询 BM25 索引。

        Returns:
            [{"chunk_id": str, "score": float}, ...]
        """
        if not self._index:
            raise ValueError("Index not loaded. Call load() or build() first.")
        if not query_terms:
            return []

        query_terms = [t.lower() for t in query_terms]
        scores: Dict[str, float] = {}

        for term in query_terms:
            if term not in self._index:
                continue
            term_data = self._index[term]
            idf = term_data["idf"]
            for posting in term_data["postings"]:
                chunk_id = posting["chunk_id"]
                tf = posting["tf"]
                doc_length = posting["doc_length"]
                term_score = self._calculate_bm25_score(
                    tf=tf, doc_length=doc_length,
                    avg_doc_length=self._metadata["avg_doc_length"], idf=idf,
                )
                scores[chunk_id] = scores.get(chunk_id, 0.0) + term_score

        sorted_results = sorted(
            [{"chunk_id": cid, "score": score} for cid, score in scores.items()],
            key=lambda x: x["score"], reverse=True,
        )
        return sorted_results[:top_k]

    def add_documents(
        self,
        term_stats: List[Dict[str, Any]],
        collection: str = "default",
        doc_id: Optional[str] = None,
    ) -> None:
        """增量添加文档。"""
        if not term_stats:
            return
        if not self._index:
            self.load(collection)
        if doc_id and self._index:
            self.remove_document(doc_id, collection)

        # 合并现有和新文档
        existing_stats: Dict[str, Dict] = {}
        for term, term_data in self._index.items():
            for posting in term_data["postings"]:
                cid = posting["chunk_id"]
                if cid not in existing_stats:
                    existing_stats[cid] = {
                        "chunk_id": cid, "term_frequencies": {},
                        "doc_length": posting["doc_length"],
                    }
                existing_stats[cid]["term_frequencies"][term] = posting["tf"]

        combined = list(existing_stats.values()) + list(term_stats)
        self.build(combined, collection)

    def remove_document(self, doc_id: str, collection: str = "default") -> bool:
        """删除文档的所有索引条目。"""
        if not self._index:
            if not self.load(collection):
                return False

        removed_any = False
        terms_to_delete = []
        for term, term_data in self._index.items():
            original_len = len(term_data["postings"])
            term_data["postings"] = [
                p for p in term_data["postings"]
                if not p["chunk_id"].startswith(doc_id)
            ]
            if len(term_data["postings"]) < original_len:
                removed_any = True
            if not term_data["postings"]:
                terms_to_delete.append(term)
            else:
                term_data["df"] = len(term_data["postings"])

        for term in terms_to_delete:
            del self._index[term]

        if removed_any:
            all_chunk_ids = set()
            total_length = 0
            for td in self._index.values():
                for p in td["postings"]:
                    all_chunk_ids.add(p["chunk_id"])
                    total_length += p["doc_length"]
            num_docs = len(all_chunk_ids)
            avg_doc_length = total_length / num_docs if num_docs else 0.0
            for td in self._index.values():
                td["idf"] = self._calculate_idf(num_docs, td["df"])
            self._metadata = {
                "num_docs": num_docs, "avg_doc_length": avg_doc_length,
                "total_terms": len(self._index), "collection": collection,
            }
            self._save(collection)
        return removed_any

    def get_term_stats_for_chunks(
        self, chunk_texts: List[Dict[str, Any]]
    ) -> List[Dict[str, Any]]:
        """从块文本生成 term_stats。

        简化版：对每个块分词去停用词，统计词频。
        Args:
            chunk_texts: [{"chunk_id": str, "text": str, ...}, ...]
        Returns:
            term_stats 列表
        """
        import jieba
        import re

        # 简单英文停用词
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

        stats = []
        for item in chunk_texts:
            text = item["text"].lower()
            # 中文分词 + 英文分词
            words = []
            # 英文单词
            for w in re.findall(r"[a-z]+", text):
                if w not in _STOP_WORDS and len(w) > 1:
                    words.append(w)
            # 中文
            for w in jieba.lcut(text):
                w = w.strip()
                if w and w not in _STOP_WORDS and len(w) > 1:
                    words.append(w)

            tf: Dict[str, int] = {}
            for w in words:
                tf[w] = tf.get(w, 0) + 1

            stats.append({
                "chunk_id": item["chunk_id"],
                "term_frequencies": tf,
                "doc_length": len(words),
            })
        return stats

    def _calculate_idf(self, num_docs: int, df: int) -> float:
        return math.log((num_docs - df + 0.5) / (df + 0.5))

    def _calculate_bm25_score(
        self, tf: int, doc_length: int, avg_doc_length: float, idf: float
    ) -> float:
        if avg_doc_length == 0:
            avg_doc_length = 1.0
        numerator = tf * (self.k1 + 1)
        denominator = tf + self.k1 * (1 - self.b + self.b * (doc_length / avg_doc_length))
        return idf * (numerator / denominator)

    def _get_index_path(self, collection: str) -> Path:
        return self.index_dir / f"{collection}_bm25.json"

    def _save(self, collection: str) -> None:
        self.index_dir.mkdir(parents=True, exist_ok=True)
        data = {"metadata": self._metadata, "index": self._index}
        path = self._get_index_path(collection)
        tmp = path.with_suffix(".tmp")
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
        tmp.replace(path)
