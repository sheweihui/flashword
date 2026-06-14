"""Agent 核心逻辑"""

from loguru import logger

from api.client import ApiClient, ApiError
from api.endpoints import Endpoints
from .memory import AgentMemory
from .tools import build_tools, _format_tool_result


class Agent:
    """背单词助手 Agent，通过后端 API 与用户数据交互"""

    def __init__(self, api_client: ApiClient):
        self.api = Endpoints(api_client)
        self.memory = AgentMemory()
        self.tools = build_tools(self.api)
        self._tool_map = {t.name: t for t in self.tools}

    # ---- 身份认证 ----

    def login(self, username: str, password: str) -> str:
        """登录并保存会话"""
        try:
            self.api._c.login(username, password)
            self.memory.put("last_user", username)
            return f"登录成功！用户: {username}"
        except ApiError as e:
            return f"登录失败: {e}"

    def register(self, username: str, password: str, nickname: str = "") -> str:
        """注册新用户"""
        try:
            self.api._c.register(username, password, nickname)
            return f"注册成功！用户: {username}"
        except ApiError as e:
            return f"注册失败: {e}"

    # ---- 核心能力 ----

    def run(self, command: str, **kwargs) -> str:
        """执行命令并返回结果"""
        tool = self._tool_map.get(command)
        if not tool:
            available = ", ".join(self._tool_map.keys())
            return f"未知命令: {command}。可用命令: {available}"

        logger.info(f"执行命令: {command} | 参数: {kwargs}")
        try:
            result = tool.fn(**kwargs)
            return _format_tool_result(command, result)
        except ApiError as e:
            return f"操作失败: {e}"
        except Exception as e:
            logger.exception(f"执行 {command} 时出错")
            return f"发生未知错误: {e}"
