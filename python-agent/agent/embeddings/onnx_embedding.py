"""ONNX 本地嵌入实现。

包装 chromadb 内置的 DefaultEmbeddingFunction()，
使用 all-MiniLM-L6-v2 ONNX 模型进行本地嵌入。
无需 torch / GPU，零外部依赖。
"""

from __future__ import annotations

from typing import List

from chromadb.utils import embedding_functions

from .base import BaseEmbedding


class ONNXEmbedding(BaseEmbedding):
    """ONNX 本地嵌入提供者。

    基于 chromadb 内置的 all-MiniLM-L6-v2 ONNX 模型。
    """

    def __init__(self, model_name: str = "all-MiniLM-L6-v2"):
        self._model_name = model_name
        self._ef = embedding_functions.DefaultEmbeddingFunction()

    async def embed(self, texts: List[str], **kwargs) -> List[List[float]]:
        """批量生成 ONNX 嵌入向量。

        注意：DefaultEmbeddingFunction() 是同步的，通过 asyncio.to_thread 执行。
        """
        import asyncio
        return await asyncio.to_thread(self._ef, texts)

    def get_dimension(self) -> int:
        """all-MiniLM-L6-v2 的嵌入维度为 384。"""
        return 384

    @property
    def name(self) -> str:
        return "onnx"
