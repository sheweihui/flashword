"""OpenAI / 兼容 API 嵌入实现。

支持 OpenAI Embeddings API 及其兼容服务（如 DeepSeek、Qwen 等）。
"""

from __future__ import annotations

import os
from typing import Any, Dict, List, Optional

from .base import BaseEmbedding


class OpenAIEmbeddingError(RuntimeError):
    """OpenAI Embeddings API 调用失败时抛出。"""


class OpenAIEmbedding(BaseEmbedding):
    """OpenAI / 兼容 API 嵌入提供者。

    支持标准 OpenAI API 及 OpenAI 兼容接口。

    Attributes:
        model: 嵌入模型名称。
        api_key: API 密钥。
        base_url: API 端点地址。
        dimensions: 向量维度。
    """

    DEFAULT_BASE_URL = "https://api.openai.com/v1"
    DEFAULT_MODEL = "text-embedding-ada-002"
    DEFAULT_DIMENSIONS = 1536

    def __init__(
        self,
        model: str = DEFAULT_MODEL,
        api_key: Optional[str] = None,
        base_url: Optional[str] = None,
        dimensions: Optional[int] = None,
    ):
        self.model = model
        self.api_key = api_key or os.environ.get("OPENAI_API_KEY")
        self.base_url = base_url or os.environ.get("OPENAI_BASE_URL") or self.DEFAULT_BASE_URL
        self.dimensions = dimensions or self.DEFAULT_DIMENSIONS

        if not self.api_key:
            raise ValueError(
                "OpenAI API key not provided. "
                "Set OPENAI_API_KEY environment variable or pass api_key parameter."
            )

    async def embed(self, texts: List[str], **kwargs) -> List[List[float]]:
        """批量生成嵌入向量。

        Args:
            texts: 待嵌入的文本列表。
            **kwargs: 可选覆盖参数。

        Returns:
            嵌入向量列表。
        """
        import asyncio
        import httpx

        headers = {
            "Authorization": f"Bearer {self.api_key}",
            "Content-Type": "application/json",
        }

        payload: Dict[str, Any] = {
            "input": texts,
            "model": self.model,
        }
        dims = kwargs.get("dimensions", self.dimensions)
        if dims and self.model.startswith("text-embedding-3"):
            payload["dimensions"] = dims

        url = f"{self.base_url.rstrip('/')}/embeddings"

        try:
            async with httpx.AsyncClient(timeout=60.0) as client:
                response = await client.post(url, json=payload, headers=headers)
                response.raise_for_status()
                data = response.json()

            embeddings = [item["embedding"] for item in data["data"]]
            if len(embeddings) != len(texts):
                raise OpenAIEmbeddingError(
                    f"Output length mismatch: expected {len(texts)}, got {len(embeddings)}"
                )
            return embeddings

        except httpx.HTTPStatusError as e:
            raise OpenAIEmbeddingError(
                f"API request failed with status {e.response.status_code}: {e.response.text}"
            ) from e
        except httpx.ConnectError as e:
            raise OpenAIEmbeddingError(
                f"Failed to connect to {self.base_url}. Check the endpoint URL."
            ) from e
        except Exception as e:
            raise OpenAIEmbeddingError(f"Embedding API call failed: {e}") from e

    def get_dimension(self) -> int:
        return self.dimensions

    @property
    def name(self) -> str:
        return "openai"
