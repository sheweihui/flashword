"""文本分块器的工厂。"""

from __future__ import annotations

from typing import Dict, Optional, Type

from .base import BaseSplitter


class SplitterFactory:
    """文本分块器工厂。

    用法：
        factory = SplitterFactory()
        factory.register("recursive", RecursiveSplitter)
        splitter = factory.create("recursive", chunk_size=500, chunk_overlap=50)
        chunks = splitter.split_text("long text...")
    """

    def __init__(self):
        self._providers: Dict[str, Type[BaseSplitter]] = {}

    def register(self, name: str, provider_class: Type[BaseSplitter]) -> None:
        if not issubclass(provider_class, BaseSplitter):
            raise ValueError(
                f"Provider class {provider_class.__name__} must inherit from BaseSplitter"
            )
        self._providers[name.lower()] = provider_class

    def create(self, provider: str, **kwargs) -> BaseSplitter:
        provider_class = self._providers.get(provider.lower())
        if provider_class is None:
            available = ", ".join(sorted(self._providers.keys()))
            raise ValueError(
                f"Unsupported splitter provider: '{provider}'. Available: {available}"
            )
        return provider_class(**kwargs)

    def list_providers(self) -> list:
        return sorted(self._providers.keys())
