"""
MCP 标准协议客户端

管理与 MCP Server 子进程的连接，提供工具调用接口。
在 server.py 中取代原先的 tools.py 直接调用方式。
"""

import sys
import asyncio
from pathlib import Path
from typing import Optional
from dataclasses import dataclass, field

from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client
from mcp.types import TextContent
from loguru import logger

from config.settings import TOOL_EXECUTION_TIMEOUT

# 项目根目录
PROJECT_ROOT = Path(__file__).resolve().parent.parent

# MCP 工具调用超时（秒）
TOOL_TIMEOUT = TOOL_EXECUTION_TIMEOUT

# 当前 Python 解释器路径（使用同一虚拟环境）
PYTHON_PATH = sys.executable

# MCP Server 脚本路径
SERVER_SCRIPT = str(PROJECT_ROOT / "mcp_server.py")


class MCPError(Exception):
    """MCP 通信异常"""
    pass


class MCPClient:
    """
    MCP 协议客户端

    启动 MCP Server 子进程，通过 stdio 传输协议进行通信。
    提供工具列表缓存、工具调用、格式化上下文等功能。

    用法：
        client = MCPClient()
        await client.connect()
        tools = await client.list_tools()
        result = await client.call_tool("search_word", {"keyword": "beautiful"})
        await client.close()
    """

    def __init__(self):
        self._session: Optional[ClientSession] = None
        self._stdio_context = None
        self._session_context = None
        self._tools_cache: list = []
        self._connected = False

    # ---------- 生命周期 ----------

    async def connect(self) -> None:
        """启动 MCP Server 子进程并建立连接"""
        if self._connected:
            return

        server_params = StdioServerParameters(
            command=PYTHON_PATH,
            args=[SERVER_SCRIPT],
            cwd=str(PROJECT_ROOT),
            env=None,  # 继承父进程环境变量
        )

        try:
            logger.info("正在启动 MCP Server 子进程...")
            self._stdio_context = stdio_client(server_params)
            read, write = await self._stdio_context.__aenter__()

            self._session_context = ClientSession(read, write)
            self._session = await self._session_context.__aenter__()
            await self._session.initialize()

            # 获取工具列表并缓存
            result = await self._session.list_tools()
            self._tools_cache = result.tools
            self._connected = True

            tool_names = [t.name for t in self._tools_cache]
            logger.info(f"MCP 连接成功 | {len(self._tools_cache)} 个工具可用: {', '.join(tool_names)}")

        except Exception as e:
            self._connected = False
            logger.error(f"MCP 连接失败: {e}")
            raise MCPError(f"无法连接到 MCP Server: {e}") from e

    async def close(self) -> None:
        """关闭 MCP 连接并终止子进程"""
        self._connected = False
        try:
            if self._session_context:
                await self._session_context.__aexit__(None, None, None)
                self._session = None
                self._session_context = None
            if self._stdio_context:
                await self._stdio_context.__aexit__(None, None, None)
                self._stdio_context = None
        except Exception as e:
            logger.debug(f"MCP 关闭时出现异常（可忽略）: {e}")
        logger.info("MCP 连接已关闭")

    @property
    def connected(self) -> bool:
        return self._connected

    # ---------- 工具调用 ----------

    async def list_tools(self) -> list:
        """获取工具列表（优先走缓存）"""
        if self._tools_cache:
            return self._tools_cache
        if self._session:
            result = await self._session.list_tools()
            self._tools_cache = result.tools
        return self._tools_cache

    async def call_tool(self, name: str, arguments: dict) -> str:
        """
        调用 MCP 工具

        参数：
            name: 工具名（如 "search_word"）
            arguments: 参数字典（如 {"keyword": "beautiful"}）

        返回：
            格式化的结果字符串（供 LLM 消费）
        """
        if not self._session or not self._connected:
            raise MCPError("MCP 未连接，无法调用工具")

        try:
            logger.info(f"MCP 调用工具: {name}({arguments})")
            result = await asyncio.wait_for(
                self._session.call_tool(name, arguments),
                timeout=TOOL_TIMEOUT,
            )

            # 从 TextContent 中提取文本
            texts = []
            for content in (result.content or []):
                if isinstance(content, TextContent):
                    texts.append(content.text)

            if not texts:
                return "操作成功（无返回数据）"

            return "\n".join(texts)

        except asyncio.TimeoutError:
            logger.error(f"MCP 工具调用超时: {name}（{TOOL_TIMEOUT}s）")
            return f"操作超时: {name} 超过 {TOOL_TIMEOUT} 秒未响应，请稍后再试"
        except Exception as e:
            logger.error(f"MCP 工具调用失败 {name}: {e}")
            return f"操作失败: {e}"

    # ---------- 工具信息格式化 ----------

    def get_tool_defs(self) -> list[dict]:
        """
        将工具列表转为 OpenAI Function Calling 格式

        供 LLM 识别可用的工具：
        [
            {
                "type": "function",
                "function": {
                    "name": "search_word",
                    "description": "查询单词的释义、音标、例句",
                    "parameters": {...}
                }
            },
            ...
        ]
        """
        defs = []
        for t in self._tools_cache:
            params = t.inputSchema or {"type": "object", "properties": {}}
            defs.append({
                "type": "function",
                "function": {
                    "name": t.name,
                    "description": t.description or "",
                    "parameters": params,
                },
            })
        return defs

    def get_tool_descriptions(self) -> str:
        """
        生成工具描述文本（用于拼接 system prompt）

        返回示例：
            - **search_word**: 查询单词的释义、音标、例句 参数: {keyword: 要查询的单词（英文）}
            - **daily_checkin**: 每日签到获取积分
        """
        lines = []
        for t in self._tools_cache:
            params_desc = ""
            try:
                props = (t.inputSchema or {}).get("properties", {})
            except Exception:
                props = {}
            if props:
                items = []
                for k, v in props.items():
                    desc = v.get("description", k) if isinstance(v, dict) else k
                    items.append(f"{k}: {desc}")
                if items:
                    params_desc = f" 参数: {{{', '.join(items)}}}"
            lines.append(f"- **{t.name}**: {t.description or ''}{params_desc}")

        return "\n".join(lines) if lines else "暂无可用操作。"
