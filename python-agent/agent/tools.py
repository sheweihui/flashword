"""Agent 工具集 — 可被 Agent 调用的能力"""

import functools
import threading
from typing import Callable, Optional
from dataclasses import dataclass, field
from loguru import logger

from api.endpoints import Endpoints
from api.client import ApiError
from config.settings import TOOL_EXECUTION_TIMEOUT
from agent.formatters import (
    format_word_result,
    format_flash_sales,
    format_book_list,
    format_word_list,
)


@dataclass
class Tool:
    name: str
    description: str
    fn: Callable
    parameters: dict = field(default_factory=dict)
    timeout: int = TOOL_EXECUTION_TIMEOUT  # 单工具执行超时（秒）


def build_tools(api: Endpoints) -> list[Tool]:
    """构建 Agent 可用的工具列表"""
    return [
        Tool(
            name="search_word",
            description="查询单词的释义、音标、例句",
            fn=api.search_word,
            parameters={"keyword": "要查询的单词（英文）"},
        ),
        Tool(
            name="ai_fill_word",
            description="使用 AI 自动补全单词的音标、释义、例句等信息",
            fn=api.ai_fill_word,
            parameters={"word_text": "单词（英文）"},
        ),
        Tool(
            name="get_points_balance",
            description="查看当前用户的积分余额",
            fn=api.get_points_balance,
        ),
        Tool(
            name="daily_checkin",
            description="每日签到获取积分",
            fn=api.checkin,
        ),
        Tool(
            name="get_store_books",
            description="获取商店中的单词书列表",
            fn=lambda page=1, size=10: api.get_store_books(page, size),
            parameters={"page": "页码（可选，默认1）", "size": "每页数量（可选，默认10）"},
        ),
        Tool(
            name="get_flash_sale_list",
            description="查看当前秒杀活动列表",
            fn=api.get_flash_sale_list,
        ),
        Tool(
            name="get_user_books",
            description="获取用户的单词本列表",
            fn=lambda: api.get_book_list(0),
        ),
        Tool(
            name="get_book_words",
            description="获取单词本中的单词列表",
            fn=api.get_words_by_book,
            parameters={"book_id": "单词本 ID"},
        ),
    ]


# ============================================================
# 工具映射缓存（避免每次执行都重建）
# ============================================================

@functools.lru_cache(maxsize=1)
def _build_tool_map(api_id: int) -> dict[str, Tool]:
    """构建工具名→工具的映射（缓存，api_id 仅用于区分实例）"""
    # 实际 api 实例无法 hash，此处仅在第一次调用时构建
    raise RuntimeError("不应直接调用，请使用 get_tool_map()")


_tool_map_cache: dict[str, Tool] = {}
_tool_map_owner: object = None


def get_tool_map(api: Endpoints) -> dict[str, Tool]:
    """获取全局工具映射（按需构建，仅一次）"""
    global _tool_map_cache, _tool_map_owner
    if _tool_map_owner is not api:
        _tool_map_cache = {t.name: t for t in build_tools(api)}
        _tool_map_owner = api
    return _tool_map_cache


# ============================================================
# OpenAI Function Calling 格式转换
# ============================================================

def to_openai_tool(tool: Tool) -> dict:
    """将 Tool 转换为 OpenAI function calling 格式"""
    properties = {}
    required = []
    for k, v in (tool.parameters or {}).items():
        properties[k] = {"type": "string", "description": v}
        required.append(k)

    return {
        "type": "function",
        "function": {
            "name": tool.name,
            "description": tool.description,
            "parameters": {
                "type": "object",
                "properties": properties,
                "required": required,
            },
        },
    }


def to_openai_tools(tools: list[Tool]) -> list[dict]:
    """批量转换工具列表为 OpenAI 格式"""
    return [to_openai_tool(t) for t in tools]


# ============================================================
# 工具执行（含超时控制）
# ============================================================

def _run_with_timeout(fn: Callable, args: dict, timeout: int):
    """在单独的线程中执行工具函数，支持超时中断"""
    result_container = []
    exception_container = []

    def target():
        try:
            result_container.append(fn(**args))
        except Exception as e:
            exception_container.append(e)

    thread = threading.Thread(target=target, daemon=True)
    thread.start()
    thread.join(timeout=timeout)

    if thread.is_alive():
        raise TimeoutError(f"工具执行超时（{timeout}s）")

    if exception_container:
        raise exception_container[0]

    return result_container[0]


def execute_tool(tool_name: str, arguments: dict, api: Endpoints) -> str:
    """执行工具并返回可读的结果字符串"""
    tool_map = get_tool_map(api)
    tool = tool_map.get(tool_name)
    if not tool:
        return f"错误：未知操作 '{tool_name}'"

    logger.info(f"执行工具: {tool_name} | 参数: {arguments}")
    try:
        result = _run_with_timeout(tool.fn, arguments, tool.timeout)
        return _format_tool_result(tool_name, result)
    except TimeoutError as e:
        logger.error(f"工具 {tool_name} 执行超时")
        return f"操作超时: {e}"
    except ApiError as e:
        logger.error(f"工具 {tool_name} API 错误: {e}")
        return f"操作失败: {e}"
    except Exception as e:
        logger.exception(f"执行工具 {tool_name} 异常")
        return f"执行出错: {e}"


# ============================================================
# 结果格式化
# ============================================================

def _format_tool_result(name: str, result) -> str:
    """格式化工具执行结果（字符串形式，供 LLM 消费）"""
    if result is None:
        return "操作成功（无返回数据）"

    if name in ("search_word", "ai_fill_word"):
        return format_word_result(result)
    if name == "get_points_balance":
        return f"当前积分余额: {result.get('balance', 'N/A')}"
    if name == "daily_checkin":
        return (f"签到成功! 获得 {result.get('pointsEarned', 0)} 积分, "
                f"连续签到 {result.get('continuousDays', 0)} 天. "
                f"{result.get('message', '')}")
    if name == "get_flash_sale_list":
        return format_flash_sales(result)
    if name in ("get_store_books", "get_user_books"):
        return format_book_list(result)
    if name == "get_book_words":
        return format_word_list(result)

    return str(result)
