"""原始固定长度分块策略的适配器（向后兼容）。"""

from __future__ import annotations

from typing import List

from .base import BaseSplitter


class FixedSplitter(BaseSplitter):
    """固定长度分块器。

    与之前 KnowledgeBase._chunk_text() 行为一致：
    - 优先在段落边界切
    - 其次在句子边界切
    - 最后硬切
    """

    def __init__(self, chunk_size: int = 500, overlap: int = 50):
        self._chunk_size = chunk_size
        self._overlap = overlap

    def split_text(self, text: str) -> List[str]:
        if not text:
            return []

        text = text.strip()
        if len(text) <= self._chunk_size:
            return [text]

        chunks = []
        start = 0

        while start < len(text):
            if start + self._chunk_size >= len(text):
                chunks.append(text[start:])
                break

            candidate = text[start:start + self._chunk_size]

            # 优先在段落边界切
            para_break = candidate.rfind("\n\n")
            if para_break > self._chunk_size // 2:
                end = start + para_break
                chunks.append(text[start:end])
                start = end
                continue

            # 其次在句子边界切
            for sep in ("。", "！", "？", "！", ". ", "! ", "? "):
                last_sep = candidate.rfind(sep)
                if last_sep > self._chunk_size // 2:
                    end = start + last_sep + len(sep)
                    chunks.append(text[start:end])
                    start = end
                    break
            else:
                chunks.append(candidate)
                start = start + self._chunk_size

            start = start - self._overlap

        return chunks

    @property
    def name(self) -> str:
        return "fixed"

    @property
    def chunk_size(self) -> int:
        return self._chunk_size

    @property
    def chunk_overlap(self) -> int:
        return self._overlap
