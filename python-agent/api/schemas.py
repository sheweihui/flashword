"""请求/响应数据模型"""

from pydantic import BaseModel
from typing import Optional


class ChatRequest(BaseModel):
    """对话请求"""
    message: str
    user_id: int  # 必填，作为会话唯一标识
    token: Optional[str] = None  # 后端 JWT token，用于 agent 调用后端 API


class ChatResponse(BaseModel):
    """对话响应"""
    reply: str
    conversation_id: str  # 内部使用，前端无需关注


class WordEnrichRequest(BaseModel):
    """单词补全请求"""
    word_text: str
    user_id: Optional[int] = None


class WordEnrichResponse(BaseModel):
    """单词补全响应"""
    content: str
    word_text: str


class ErrorResponse(BaseModel):
    """错误响应"""
    error: str
    code: int = -1


# ---- 知识库 ----

class KnowledgeUploadRequest(BaseModel):
    """知识库上传请求"""
    title: str
    content: str
    user_id: Optional[int] = None


class KnowledgeUploadResponse(BaseModel):
    """知识库上传响应"""
    id: str
    title: str
    chunk_count: int
    message: str


class KnowledgeDocument(BaseModel):
    """知识库文档信息"""
    id: str
    title: str
    chunk_count: int
