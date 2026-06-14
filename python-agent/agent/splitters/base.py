"""文本分块器的抽象基类。"""

from __future__ import annotations

from abc import ABC, abstractmethod
from typing import List


class BaseSplitter(ABC):
    """文本分块器基类。

    所有分块实现必须继承此类并实现 split_text() 方法。
    """

    @abstractmethod
    def split_text(self, text: str) -> List[str]:
        """将文本分割为多个块。

        Args:
            text: 待分割的文本。

        Returns:
            分割后的文本块列表。
        """
        ...

    @property
    @abstractmethod
    def name(self) -> str:
        """分块器名称标识。"""
        ...
