"""基于 LangChain RecursiveCharacterTextSplitter 的分块实现。"""

from __future__ import annotations

from typing import List, Optional

from langchain_text_splitters import RecursiveCharacterTextSplitter as _LCSplitter

from .base import BaseSplitter


class RecursiveSplitter(BaseSplitter):
    """递归字符文本分块器。

    按优先级在段落(\n\n)、句子、标点等边界切分，
    尽量保持语义完整性。
    """

    def __init__(
        self,
        chunk_size: int = 500,
        chunk_overlap: int = 50,
        separators: Optional[List[str]] = None,
    ):
        self._chunk_size = chunk_size
        self._chunk_overlap = chunk_overlap
        self._separators = separators or ["\n\n", "\n", ". ", "! ", "? ", "; ", ", ", " ", ""]

        self._splitter = _LCSplitter(
            chunk_size=chunk_size,
            chunk_overlap=chunk_overlap,
            separators=self._separators,
        )

    def split_text(self, text: str) -> List[str]:
        if not text or not text.strip():
            return []
        return self._splitter.split_text(text)

    @property
    def name(self) -> str:
        return "recursive"

    @property
    def chunk_size(self) -> int:
        return self._chunk_size

    @property
    def chunk_overlap(self) -> int:
        return self._chunk_overlap
