"""Ollama 本地嵌入实现。

支持通过 Ollama 运行本地嵌入模型（如 nomic-embed-text）。
"""

from __future__ import annotations

import os
from typing import List, Optional

from .base import BaseEmbedding


class OllamaEmbeddingError(RuntimeError):
    """Ollama Embedding API 调用失败时抛出。"""


class OllamaEmbedding(BaseEmbedding):
    """Ollama 本地嵌入提供者。

    通过 Ollama 的 HTTP API 调用本地嵌入模型。

    Attributes:
        base_url: Ollama 服务地址。
        model: 嵌入模型名称（如 nomic-embed-text）。
        timeout: 请求超时秒数。
    """

    DEFAULT_BASE_URL = "http://localhost:11434"
    DEFAULT_TIMEOUT = 120.0

    def __init__(
        self,
        model: str = "nomic-embed-text",
        base_url: Optional[str] = None,
        timeout: Optional[float] = None,
    ):
        self.model = model
        self.base_url = (
            base_url
            or os.environ.get("OLLAMA_BASE_URL")
            or self.DEFAULT_BASE_URL
        )
        self.timeout = timeout or self.DEFAULT_TIMEOUT

    async def embed(self, texts: List[str], **kwargs) -> List[List[float]]:
        """批量生成嵌入向量。

        Ollama API 目前不支持批量嵌入，每个文本单独请求。
        """
        import httpx

        url = f"{self.base_url.rstrip('/')}/api/embeddings"
        embeddings: List[List[float]] = []

        async with httpx.AsyncClient(timeout=self.timeout) as client:
            for text in texts:
                payload = {"model": self.model, "prompt": text}
                try:
                    response = await client.post(url, json=payload)
                    response.raise_for_status()
                    result = response.json()
                    if "embedding" not in result:
                        raise OllamaEmbeddingError(
                            f"Unexpected response format: {list(result.keys())}"
                        )
                    embeddings.append(result["embedding"])
                except httpx.ConnectError as e:
                    raise OllamaEmbeddingError(
                        f"Failed to connect to Ollama at {self.base_url}. "
                        f"Is Ollama running? (try: ollama serve)"
                    ) from e
                except httpx.HTTPStatusError as e:
                    raise OllamaEmbeddingError(
                        f"Ollama API error {e.response.status_code}: {e.response.text}"
                    ) from e

        return embeddings

    def get_dimension(self) -> int:
        """nomic-embed-text 的嵌入维度为 768。"""
        return 768

    @property
    def name(self) -> str:
        return "ollama"
