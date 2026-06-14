"""嵌入提供者的工厂。

通过注册表模式实现可插拔嵌入后端的切换。
"""

from __future__ import annotations

from typing import Dict, Optional, Type

from .base import BaseEmbedding


class EmbeddingFactory:
    """嵌入提供者工厂。

    用法：
        factory = EmbeddingFactory()
        factory.register("onnx", ONNXEmbedding)

        emb = factory.create("onnx", model_name="all-MiniLM-L6-v2")
        vectors = await emb.embed(["hello"])
    """

    def __init__(self):
        self._providers: Dict[str, Type[BaseEmbedding]] = {}

    def register(self, name: str, provider_class: Type[BaseEmbedding]) -> None:
        """注册一个嵌入提供者。

        Args:
            name: 提供者标识符（如 'onnx', 'openai', 'ollama'）。
            provider_class: BaseEmbedding 的子类。

        Raises:
            ValueError: 如果 provider_class 不是 BaseEmbedding 的子类。
        """
        if not issubclass(provider_class, BaseEmbedding):
            raise ValueError(
                f"Provider class {provider_class.__name__} must inherit from BaseEmbedding"
            )
        self._providers[name.lower()] = provider_class

    def create(self, provider: str, **kwargs) -> BaseEmbedding:
        """创建一个嵌入提供者实例。

        Args:
            provider: 已在工厂中注册的提供者名称。
            **kwargs: 传递给提供者构造函数的参数。

        Returns:
            BaseEmbedding 实例。

        Raises:
            ValueError: 如果提供者未注册。
        """
        provider_class = self._providers.get(provider.lower())
        if provider_class is None:
            available = ", ".join(sorted(self._providers.keys()))
            raise ValueError(
                f"Unsupported embedding provider: '{provider}'. "
                f"Available: {available}"
            )
        return provider_class(**kwargs)

    def list_providers(self) -> list:
        """列出所有已注册的提供者名称。"""
        return sorted(self._providers.keys())

    def has_provider(self, name: str) -> bool:
        """检查指定名称的提供者是否已注册。"""
        return name.lower() in self._providers
