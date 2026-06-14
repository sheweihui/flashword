"""嵌入提供者的抽象基类。

定义了可插拔的嵌入接口，不同的后端（ONNX / OpenAI / Ollama）
通过实现此接口实现无缝切换。
"""

from __future__ import annotations

from abc import ABC, abstractmethod
from typing import List, Optional


class BaseEmbedding(ABC):
    """嵌入提供者的抽象基类。

    所有嵌入实现必须继承此类并实现 embed() 方法。
    """

    @abstractmethod
    async def embed(self, texts: List[str], **kwargs) -> List[List[float]]:
        """批量生成文本的嵌入向量。

        Args:
            texts: 待嵌入的文本列表。
            **kwargs: 提供者特定的参数。

        Returns:
            嵌入向量列表，每个向量是一个 float 列表。
        """
        ...

    @abstractmethod
    def get_dimension(self) -> int:
        """获取此提供者产生的嵌入向量维度。"""
        ...

    @property
    @abstractmethod
    def name(self) -> str:
        """提供者名称标识。"""
        ...
