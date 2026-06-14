"""
降级管理器：组件健康追踪 + 熔断器 + 降级策略

解决的问题：
  系统依赖多个外部组件（LLM、后端 API、MCP、ChromaDB），
  任何一个不稳定都不应拖垮整体。降级管理器统一追踪各组件健康状态，
  达到阈值后自动熔断并切换降级策略。

熔断器状态机：
  CLOSED（正常）→ OPEN（熔断）→ HALF_OPEN（半开探活）→ CLOSED 或 OPEN

用法：
    dm = DegradationManager()
    dm.record_success("llm")
    dm.record_failure("mcp")
    if dm.get_status("mcp") == "degraded":
        strategy = dm.get_strategy("mcp")  # → "skip_fallback"
"""

import time
from dataclasses import dataclass, field
from enum import Enum
from typing import Optional

from loguru import logger


# ---------------------------------------------------------------
# 组件定义
# ---------------------------------------------------------------

class Component(str, Enum):
    LLM = "llm"
    MCP = "mcp"
    BACKEND_API = "backend_api"
    CHROMADB = "chromadb"


# 熔断器状态
class CircuitState(str, Enum):
    CLOSED = "closed"        # 正常
    OPEN = "open"            # 熔断中
    HALF_OPEN = "half_open"  # 试探性恢复


# 降级策略（从最重到最轻）
class FallbackStrategy(str, Enum):
    NORMAL = "normal"              # 正常运行
    RETRY_ONCE = "retry_once"      # 先重试一次
    SKIP_FALLBACK = "skip"         # 跳过此组件，走替代方案
    LOCAL_ONLY = "local_only"      # 仅使用本地能力
    BLOCK = "block"                # 阻断请求（极端情况）


# ---------------------------------------------------------------
# 组件配置
# ---------------------------------------------------------------

@dataclass
class ComponentConfig:
    """每个组件的熔断阈值"""
    failure_threshold: int = 3          # 连续失败 N 次 → OPEN
    half_open_max_retries: int = 2      # 半开状态下最多成功 N 次后恢复
    cooldown_seconds: int = 30          # OPEN → HALF_OPEN 等待时间
    slow_threshold_ms: int = 5000       # 响应超过此值算慢查询（仅记录）


_DEFAULT_CONFIGS: dict[str, ComponentConfig] = {
    Component.LLM:        ComponentConfig(failure_threshold=3, cooldown_seconds=30),
    Component.MCP:        ComponentConfig(failure_threshold=3, cooldown_seconds=15),
    Component.BACKEND_API: ComponentConfig(failure_threshold=5, cooldown_seconds=30),
    Component.CHROMADB:   ComponentConfig(failure_threshold=2, cooldown_seconds=10),
}


# ---------------------------------------------------------------
# 组件健康状态
# ---------------------------------------------------------------

@dataclass
class ComponentHealth:
    """单个组件的运行时健康状态"""
    state: CircuitState = CircuitState.CLOSED
    consecutive_failures: int = 0
    consecutive_successes: int = 0
    last_failure_time: float = 0.0
    last_success_time: float = 0.0
    total_calls: int = 0
    total_failures: int = 0
    total_slow: int = 0

    @property
    def availability(self) -> float:
        """可用率 [0, 1]"""
        if self.total_calls == 0:
            return 1.0
        return 1.0 - (self.total_failures / self.total_calls)

    @property
    def degraded(self) -> bool:
        return self.state != CircuitState.CLOSED

    def summary(self) -> str:
        return (
            f"state={self.state.value}, "
            f"failures={self.consecutive_failures}/{self.total_failures}, "
            f"availability={self.availability:.0%}"
        )


# ---------------------------------------------------------------
# 降级管理器
# ---------------------------------------------------------------

class DegradationManager:
    """
    降级管理器

    用法：
        dm = DegradationManager()
        dm.record_success("mcp")
        dm.record_failure("llm")

        status = dm.get_status("llm")        # → "healthy" / "degraded" / "down"
        strategy = dm.get_strategy("mcp")     # → FallbackStrategy

        report = dm.get_report()              # → 所有组件状态汇总
    """

    def __init__(self, configs: Optional[dict[str, ComponentConfig]] = None):
        self._configs: dict[str, ComponentConfig] = configs or {
            k.value if isinstance(k, Component) else k: v
            for k, v in _DEFAULT_CONFIGS.items()
        }
        # 确保所有内置组件都有配置
        for comp in Component:
            key = comp.value
            if key not in self._configs:
                self._configs[key] = _DEFAULT_CONFIGS[comp]

        self._health: dict[str, ComponentHealth] = {
            comp.value if isinstance(comp, Component) else comp: ComponentHealth()
            for comp in Component
        }

        logger.info(
            f"降级管理器已初始化: {len(self._health)} 个组件 "
            f"(阈值: { {k: v.failure_threshold for k, v in self._configs.items()} })"
        )

    # ---------------------------------------------------------------
    # 状态上报
    # ---------------------------------------------------------------

    def record_success(self, component: str, elapsed_ms: float = 0):
        """记录一次成功的调用"""
        health = self._health.get(component)
        if not health:
            return

        health.total_calls += 1
        health.last_success_time = time.time()

        if elapsed_ms > self._configs[component].slow_threshold_ms:
            health.total_slow += 1
            logger.warning(f"组件 [{component}] 慢查询: {elapsed_ms:.0f}ms")

        if health.state == CircuitState.OPEN:
            # OPEN 状态下收到成功 → 切换到 HALF_OPEN
            health.state = CircuitState.HALF_OPEN
            health.consecutive_successes = 1
            health.consecutive_failures = 0
            logger.info(f"组件 [{component}] OPEN → HALF_OPEN")
            return

        if health.state == CircuitState.HALF_OPEN:
            health.consecutive_successes += 1
            threshold = self._configs[component].half_open_max_retries
            if health.consecutive_successes >= threshold:
                health.state = CircuitState.CLOSED
                health.consecutive_failures = 0
                logger.info(f"组件 [{component}] HALF_OPEN → CLOSED (恢复)")
            return

        # CLOSED: 重置连续失败计数
        health.consecutive_failures = 0

    def record_failure(self, component: str):
        """记录一次失败的调用"""
        health = self._health.get(component)
        if not health:
            return

        health.total_calls += 1
        health.total_failures += 1
        health.consecutive_failures += 1
        health.last_failure_time = time.time()

        threshold = self._configs[component].failure_threshold

        if health.state == CircuitState.HALF_OPEN:
            # 半开时失败 → 立即回到 OPEN
            health.state = CircuitState.OPEN
            health.consecutive_successes = 0
            logger.warning(f"组件 [{component}] HALF_OPEN → OPEN (探活失败)")
            return

        if (health.state == CircuitState.CLOSED
                and health.consecutive_failures >= threshold):
            health.state = CircuitState.OPEN
            logger.warning(f"组件 [{component}] CLOSED → OPEN "
                           f"(连续 {threshold} 次失败)")
            return

    # ---------------------------------------------------------------
    # 状态查询
    # ---------------------------------------------------------------

    def get_status(self, component: str) -> str:
        """
        返回组件状态：
          - "healthy":  可正常使用
          - "degraded": 降级中（HALF_OPEN / 可用率低）
          - "down":     不可用（OPEN）
        """
        health = self._health.get(component)
        if not health:
            return "unknown"

        if health.state == CircuitState.OPEN:
            return "down"

        if health.state != CircuitState.CLOSED:
            return "degraded"

        # 即使 CLOSED，如果可用率低于 70% 也算 degraded
        if health.total_calls >= 5 and health.availability < 0.7:
            return "degraded"

        return "healthy"

    def get_strategy(self, component: str) -> FallbackStrategy:
        """
        返回当前应使用的降级策略
        """
        status = self.get_status(component)

        if status == "down":
            config = self._configs.get(component)

            if component == Component.LLM.value:
                return FallbackStrategy.LOCAL_ONLY
            elif component in (Component.MCP.value, Component.BACKEND_API.value):
                return FallbackStrategy.SKIP_FALLBACK
            elif component == Component.CHROMADB.value:
                return FallbackStrategy.SKIP_FALLBACK
            return FallbackStrategy.SKIP_FALLBACK

        if status == "degraded":
            if component == Component.LLM.value:
                return FallbackStrategy.RETRY_ONCE
            return FallbackStrategy.RETRY_ONCE

        return FallbackStrategy.NORMAL

    def should_try(self, component: str) -> bool:
        """判断是否应该尝试调用此组件（熔断跳过）"""
        health = self._health.get(component)
        if not health:
            return True

        if health.state == CircuitState.OPEN:
            # 检查冷却时间
            elapsed = time.time() - health.last_failure_time
            cooldown = self._configs[component].cooldown_seconds
            if elapsed < cooldown:
                return False
            # 冷却完毕，允许一次探活
            logger.info(f"组件 [{component}] 冷却期满，尝试探活")
            health.state = CircuitState.HALF_OPEN
            return True

        return True

    # ---------------------------------------------------------------
    # 管理与报告
    # ---------------------------------------------------------------

    def get_report(self) -> list[dict]:
        """获取所有组件的状态报告（供 API / 监控使用）"""
        report = []
        for comp_name, health in self._health.items():
            config = self._configs.get(comp_name)
            report.append({
                "component": comp_name,
                "state": health.state.value,
                "status": self.get_status(comp_name),
                "strategy": self.get_strategy(comp_name).value,
                "availability": round(health.availability, 3),
                "total_calls": health.total_calls,
                "total_failures": health.total_failures,
                "consecutive_failures": health.consecutive_failures,
                "slow_queries": health.total_slow,
                "failure_threshold": config.failure_threshold if config else None,
            })
        return report

    def reset_component(self, component: str):
        """手动重置某个组件（管理接口）"""
        if component in self._health:
            self._health[component] = ComponentHealth()
            logger.info(f"组件 [{component}] 已手动重置")

    def reset_all(self):
        """手动重置所有组件"""
        for comp in self._health:
            self._health[comp] = ComponentHealth()
        logger.info("所有组件已手动重置")
