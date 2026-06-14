"""本地记忆存储"""

import json
import time
from pathlib import Path
from typing import Optional

MEMORY_FILE = Path(__file__).resolve().parent.parent / "data" / "memory.json"


class AgentMemory:
    """简单的键值对记忆存储，持久化到 JSON 文件"""

    def __init__(self, max_items: int = 100):
        self.max_items = max_items
        self._store: dict = {}
        self._load()

    def put(self, key: str, value: any) -> None:
        self._store[key] = {"value": value, "timestamp": time.time()}
        self._trim()
        self._save()

    def get(self, key: str) -> Optional[any]:
        entry = self._store.get(key)
        return entry["value"] if entry else None

    def delete(self, key: str) -> None:
        self._store.pop(key, None)
        self._save()

    def clear(self) -> None:
        self._store.clear()
        self._save()

    def all(self) -> dict:
        return {k: v["value"] for k, v in self._store.items()}

    def _trim(self) -> None:
        if len(self._store) > self.max_items:
            # 移除最旧的一半
            sorted_keys = sorted(self._store, key=lambda k: self._store[k]["timestamp"])
            for k in sorted_keys[: len(self._store) // 2]:
                del self._store[k]

    def _load(self) -> None:
        if MEMORY_FILE.exists():
            try:
                with open(MEMORY_FILE, "r", encoding="utf-8") as f:
                    self._store = json.load(f)
            except json.JSONDecodeError:
                pass

    def _save(self) -> None:
        MEMORY_FILE.parent.mkdir(parents=True, exist_ok=True)
        with open(MEMORY_FILE, "w", encoding="utf-8") as f:
            json.dump(self._store, f, ensure_ascii=False, indent=2)
