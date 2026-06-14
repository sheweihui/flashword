"""Trace 追踪 — 记录 RAG 各阶段耗时与中间数据。"""

from __future__ import annotations

import json
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional


TRACE_LOG_DIR = Path(__file__).resolve().parent.parent / "logs"
TRACE_LOG_FILE = TRACE_LOG_DIR / "traces.jsonl"


def _ensure_log_dir():
    TRACE_LOG_DIR.mkdir(parents=True, exist_ok=True)


class TraceContext:
    """请求级别的追踪上下文。

    记录 RAG 检索各阶段的耗时、数据和错误信息，
    最终序列化为 JSON Lines 写入 logs/traces.jsonl。

    用法：
        trace = TraceContext("query")
        with trace.stage("word_search"):
            results = search_words()
        trace.set_metadata("user_id", 1)
        trace.finish()  # 写入 JSONL
    """

    def __init__(self, trace_type: str = "query"):
        self.trace_id = uuid.uuid4().hex[:12]
        self.trace_type = trace_type
        self.started_at = datetime.now(timezone.utc).isoformat()
        self.finished_at: Optional[str] = None
        self._stages: List[Dict[str, Any]] = []
        self._metadata: Dict[str, Any] = {}
        self._start_mono = time.monotonic()

    def set_metadata(self, key: str, value: Any) -> None:
        self._metadata[key] = value

    def record_stage(
        self,
        name: str,
        elapsed_ms: float,
        data: Optional[Dict[str, Any]] = None,
        error: Optional[str] = None,
    ) -> None:
        """记录一个阶段的执行结果。

        Args:
            name: 阶段名称。
            elapsed_ms: 耗时毫秒。
            data: 阶段相关的数据（结果数量、样本等）。
            error: 错误信息（如有）。
        """
        entry: Dict[str, Any] = {
            "stage": name,
            "elapsed_ms": round(elapsed_ms, 2),
        }
        if data:
            entry["data"] = data
        if error:
            entry["error"] = error
        self._stages.append(entry)

    def finish(self) -> None:
        """标记追踪结束并写入日志文件。"""
        self.finished_at = datetime.now(timezone.utc).isoformat()
        total_ms = (time.monotonic() - self._start_mono) * 1000

        record = {
            "trace_id": self.trace_id,
            "trace_type": self.trace_type,
            "started_at": self.started_at,
            "finished_at": self.finished_at,
            "total_elapsed_ms": round(total_ms, 2),
            "stages": self._stages,
            "metadata": self._metadata,
        }

        _ensure_log_dir()
        try:
            with open(TRACE_LOG_FILE, "a", encoding="utf-8") as f:
                f.write(json.dumps(record, ensure_ascii=False) + "\n")
        except Exception:
            pass

    def get_summary(self) -> str:
        """返回可读的追踪摘要。"""
        parts = [f"Trace {self.trace_id} ({self.trace_type})"]
        for s in self._stages:
            err = f" ERROR: {s.get('error')}" if s.get("error") else ""
            parts.append(f"  {s['stage']}: {s['elapsed_ms']:.0f}ms{err}")
        parts.append(f"  total: {(time.monotonic() - self._start_mono) * 1000:.0f}ms")
        return "\n".join(parts)
