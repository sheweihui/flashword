"""应用配置"""

import os
from dotenv import load_dotenv

load_dotenv()

# 后端 API 配置
API_BASE_URL = os.getenv("API_BASE_URL", "http://localhost:8080/api")
API_TIMEOUT = int(os.getenv("API_TIMEOUT", "30"))

# Agent 服务配置
AGENT_HOST = os.getenv("AGENT_HOST", "0.0.0.0")
AGENT_PORT = int(os.getenv("AGENT_PORT", "8000"))
AGENT_NAME = os.getenv("AGENT_NAME", "背单词助手")
AGENT_VERSION = "0.2.0"

# LLM 配置 (DeepSeek, 兼容 OpenAI SDK)
LLM_API_KEY = os.getenv("LLM_API_KEY", "")
LLM_BASE_URL = os.getenv("LLM_BASE_URL", "https://api.deepseek.com/v1")
LLM_MODEL = os.getenv("LLM_MODEL", "deepseek-chat")
LLM_TIMEOUT = int(os.getenv("LLM_TIMEOUT", "60"))
LLM_MAX_TOKENS = int(os.getenv("LLM_MAX_TOKENS", "1024"))
LLM_TEMPERATURE = float(os.getenv("LLM_TEMPERATURE", "0.7"))

# LLM 重试配置
LLM_RETRY_MAX = int(os.getenv("LLM_RETRY_MAX", "3"))
LLM_RETRY_DELAY = float(os.getenv("LLM_RETRY_DELAY", "1.0"))

# 工具执行配置
TOOL_EXECUTION_TIMEOUT = int(os.getenv("TOOL_EXECUTION_TIMEOUT", "15"))

# Tool Calling 配置
TOOL_CALL_MAX_ROUNDS = int(os.getenv("TOOL_CALL_MAX_ROUNDS", "3"))
CONVERSATION_MAX_AGE_DAYS = int(os.getenv("CONVERSATION_MAX_AGE_DAYS", "7"))

# 日志配置
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO")
LOG_FILE = os.getenv("LOG_FILE", "data/agent.log")

# Redis 缓存
REDIS_HOST = os.getenv("REDIS_HOST", "localhost")
REDIS_PORT = int(os.getenv("REDIS_PORT", "6379"))
REDIS_DB = int(os.getenv("REDIS_DB", "0"))
REDIS_PASSWORD = os.getenv("REDIS_PASSWORD", "")

# 数据存储
DATA_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), "data")

# Embedding 配置
EMBEDDING_PROVIDER = os.getenv("EMBEDDING_PROVIDER", "onnx")  # onnx | openai | ollama
EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "all-MiniLM-L6-v2")
EMBEDDING_DIMS = int(os.getenv("EMBEDDING_DIMS", "384"))  # 384 for ONNX, 768 for nomic, 1536 for ada

# 分块配置
SPLITTER_PROVIDER = os.getenv("SPLITTER_PROVIDER", "recursive")  # recursive | fixed
CHUNK_SIZE = int(os.getenv("CHUNK_SIZE", "500"))
CHUNK_OVERLAP = int(os.getenv("CHUNK_OVERLAP", "50"))

# 混合搜索配置
HYBRID_SEARCH_ENABLED = os.getenv("HYBRID_SEARCH_ENABLED", "true").lower() == "true"
HYBRID_SEARCH_TOP_K_DENSE = int(os.getenv("HYBRID_SEARCH_TOP_K_DENSE", "20"))
HYBRID_SEARCH_TOP_K_SPARSE = int(os.getenv("HYBRID_SEARCH_TOP_K_SPARSE", "20"))
HYBRID_SEARCH_TOP_K_FUSION = int(os.getenv("HYBRID_SEARCH_TOP_K_FUSION", "10"))
HYBRID_SEARCH_RRF_K = int(os.getenv("HYBRID_SEARCH_RRF_K", "60"))
BM25_INDEX_DIR = os.getenv("BM25_INDEX_DIR", os.path.join(os.path.dirname(os.path.dirname(__file__)), "data", "bm25"))
