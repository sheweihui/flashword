"""LLM 集成：对接 DeepSeek API（兼容 OpenAI SDK）"""

import json
import time
from typing import Optional
from openai import OpenAI, APIError, APITimeoutError, RateLimitError
from loguru import logger

from config.settings import (
    LLM_API_KEY,
    LLM_BASE_URL,
    LLM_MODEL,
    LLM_TIMEOUT,
    LLM_MAX_TOKENS,
    LLM_TEMPERATURE,
    LLM_RETRY_MAX,
    LLM_RETRY_DELAY,
)


class LLMClient:
    """封装 LLM 调用，支持重试与超时控制"""

    def __init__(
        self,
        api_key: str = LLM_API_KEY,
        base_url: str = LLM_BASE_URL,
        model: str = LLM_MODEL,
    ):
        if not api_key:
            logger.warning("LLM_API_KEY 未配置，对话功能将不可用")
        self.model = model
        self.client = OpenAI(
            api_key=api_key,
            base_url=base_url,
            timeout=LLM_TIMEOUT,
        )

    def _call(self, **kwargs) -> tuple[str, list[dict]]:
        """
        统一的 LLM 调用入口，包含重试逻辑。

        返回 (content, tool_calls_list)
        """
        last_error = None
        for attempt in range(1, LLM_RETRY_MAX + 1):
            try:
                if attempt > 1:
                    logger.info(f"LLM 重试第 {attempt} 次")
                    time.sleep(LLM_RETRY_DELAY)

                response = self.client.chat.completions.create(**kwargs)
                message = response.choices[0].message

                content = message.content or ""
                tool_calls = []
                if message.tool_calls:
                    for tc in message.tool_calls:
                        tool_calls.append({
                            "id": tc.id,
                            "type": "function",
                            "function": {
                                "name": tc.function.name,
                                "arguments": tc.function.arguments,
                            },
                        })

                return content, tool_calls

            except RateLimitError as e:
                last_error = e
                wait = LLM_RETRY_DELAY * (2 ** (attempt - 1))  # 指数退避
                logger.warning(f"LLM 速率限制 (尝试 {attempt}/{LLM_RETRY_MAX}), 等待 {wait}s")
                time.sleep(wait)

            except APITimeoutError as e:
                last_error = e
                logger.warning(f"LLM 超时 (尝试 {attempt}/{LLM_RETRY_MAX})")

            except APIError as e:
                # 服务端错误才重试，客户端错误直接返回
                if 500 <= (e.status_code or 0) <= 599 and attempt < LLM_RETRY_MAX:
                    last_error = e
                    logger.warning(f"LLM 服务端错误 {e.status_code} (尝试 {attempt}/{LLM_RETRY_MAX})")
                else:
                    logger.error(f"LLM API 错误: {e}")
                    return f"抱歉，AI 服务出错了: {e.message}", []

            except Exception as e:
                logger.exception("LLM 调用异常")
                return f"抱歉，处理请求时发生了错误: {str(e)}", []

        logger.error(f"LLM 调用失败（已重试 {LLM_RETRY_MAX} 次）: {last_error}")
        return "抱歉，AI 服务暂时不可用，请稍后重试。", []

    def chat(
        self,
        messages: list[dict],
        system_prompt: Optional[str] = None,
    ) -> str:
        """发送对话消息并返回回复"""
        full_messages = []
        if system_prompt:
            full_messages.append({"role": "system", "content": system_prompt})
        full_messages.extend(messages)

        logger.debug(f"LLM 请求: model={self.model}, messages={len(full_messages)}条")
        content, _ = self._call(
            model=self.model,
            messages=full_messages,
            temperature=LLM_TEMPERATURE,
            max_tokens=LLM_MAX_TOKENS,
        )
        logger.debug(f"LLM 回复: {content[:100]}...")
        return content

    def chat_with_tools(
        self,
        messages: list[dict],
        system_prompt: Optional[str] = None,
        tools: Optional[list[dict]] = None,
    ) -> tuple[str, list[dict]]:
        """
        发送消息并支持工具/函数调用。

        返回 (content, tool_calls_list):
          - content: 文本回复
          - tool_calls_list: 工具调用列表
        """
        full_messages = []
        if system_prompt:
            full_messages.append({"role": "system", "content": system_prompt})
        full_messages.extend(messages)

        kwargs = dict(
            model=self.model,
            messages=full_messages,
            temperature=LLM_TEMPERATURE,
            max_tokens=LLM_MAX_TOKENS,
        )
        if tools:
            kwargs["tools"] = tools
            kwargs["tool_choice"] = "auto"

        logger.debug(f"LLM 请求 (tools={len(tools or [])}): model={self.model}, "
                     f"messages={len(full_messages)}条")
        content, tool_calls = self._call(**kwargs)
        logger.debug(f"LLM 回复: content_len={len(content)}, tool_calls={len(tool_calls)}")
        return content, tool_calls
