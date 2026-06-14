"""
MCP 标准协议服务端

作为独立进程运行，通过 stdio 与 Agent 主进程通信。
将后端 API 包装成标准 MCP 工具供 LLM 调用。

启动方式（由 MCP Client 自动启动，无需手动运行）：
    python mcp_server.py
"""

import json
import sys
from pathlib import Path

# 确保在项目根目录运行
PROJECT_ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(PROJECT_ROOT))

from mcp.server.fastmcp import FastMCP
from loguru import logger

from api.client import ApiClient, ApiError
from api.endpoints import Endpoints
from agent.formatters import (
    format_word_result,
    format_single_word,
    format_flash_sales,
    format_book_list,
    format_word_list,
)

# ---------- 后端 API 客户端 ----------
_client = ApiClient()
api = Endpoints(_client)

# data/auth.json 路径（与 server.py 共享）
AUTH_FILE = PROJECT_ROOT / "data" / "auth.json"


def _reload_auth():
    """从共享的 auth.json 重新加载登录凭证

    server.py 在每次收到前端请求时会把 token 写入 auth.json，
    这里在每个工具调用前读取，确保使用最新的登录态。
    """
    try:
        if AUTH_FILE.exists():
            with open(AUTH_FILE, "r", encoding="utf-8") as f:
                data = json.load(f)
            token = data.get("token")
            user_id = data.get("user_id", 0)
            if token:
                _client.auth.set_token(token=token, user_id=user_id)
    except Exception:
        pass  # 没有 auth 文件时使用匿名请求

# ---------- MCP 服务 ----------
mcp = FastMCP(
    "vocab-agent",
    instructions="背单词助手 AI Agent — 提供查词、签到、积分、词本等能力",
)


# ============================================================
# 工具定义
# ============================================================


@mcp.tool(description="查询单词的释义、音标、例句等信息")
def search_word(keyword: str) -> str:
    """查询单词的释义、音标、例句等信息"""
    _reload_auth()
    try:
        result = api.search_word(keyword)
        return format_word_result(result)
    except ApiError as e:
        return f"查词失败: {e}"


@mcp.tool(description="使用 AI 自动补全单词的音标、释义、例句等信息")
def ai_fill_word(word_text: str) -> str:
    """使用 AI 自动补全单词信息"""
    _reload_auth()
    try:
        result = api.ai_fill_word(word_text)
        return format_single_word(result)
    except ApiError as e:
        return f"AI 补全失败: {e}"


@mcp.tool(description="查看当前用户的积分余额")
def get_points_balance() -> str:
    """查看当前用户的积分余额"""
    _reload_auth()
    try:
        result = api.get_points_balance()
        balance = result.get("balance", 0) if isinstance(result, dict) else result
        return f"当前积分余额: {balance}"
    except ApiError as e:
        return f"查询积分失败: {e}"


@mcp.tool(description="每日签到获取积分")
def daily_checkin() -> str:
    """每日签到获取积分"""
    _reload_auth()
    try:
        result = api.checkin()
        if isinstance(result, dict):
            points = result.get("pointsEarned", 0)
            days = result.get("continuousDays", 0)
            msg = result.get("message", "")
            return f"签到成功! 获得 {points} 积分, 连续签到 {days} 天. {msg}"
        return f"签到成功: {result}"
    except ApiError as e:
        return f"签到失败: {e}"


@mcp.tool(description="获取商店中的单词书列表")
def get_store_books(page: int = 1, size: int = 10) -> str:
    """获取商店中的单词书列表"""
    _reload_auth()
    try:
        result = api.get_store_books(page, size)
        return format_book_list(result)
    except ApiError as e:
        return f"获取商店词书失败: {e}"


@mcp.tool(description="查看当前秒杀活动列表")
def get_flash_sale_list() -> str:
    """查看当前秒杀活动列表"""
    _reload_auth()
    try:
        result = api.get_flash_sale_list()
        return format_flash_sales(result)
    except ApiError as e:
        return f"获取秒杀列表失败: {e}"


@mcp.tool(description="获取当前用户的单词本列表")
def get_user_books() -> str:
    """获取当前用户的单词本列表"""
    _reload_auth()
    try:
        result = api.get_book_list(0)
        return format_book_list(result)
    except ApiError as e:
        return f"获取词本列表失败: {e}"


@mcp.tool(description="获取单词本中的所有单词列表")
def get_book_words(book_id: int) -> str:
    """获取单词本中的单词列表"""
    _reload_auth()
    try:
        result = api.get_words_by_book(book_id)
        return format_word_list(result)
    except ApiError as e:
        return f"获取词本单词失败: {e}"


# ============================================================
# 入口
# ============================================================

if __name__ == "__main__":
    # 改用 stderr 输出日志，不干扰 stdout 的 JSON-RPC 通信
    logger.remove()
    logger.add(sys.stderr, level="WARNING", format="{message}")

    # 通过 stdio 传输运行 MCP 服务
    # MCP Client 会启动本进程并通过 stdin/stdout 通信
    mcp.run(transport="stdio")
