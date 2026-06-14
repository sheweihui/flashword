"""对话历史管理"""

import json
import re
import time
import uuid
from pathlib import Path
from typing import Optional

CONV_DIR = Path(__file__).resolve().parent.parent / "data" / "conversations"

# 超过此条数时触发摘要压缩
SUMMARY_THRESHOLD = 15
# 保留最近多少条不摘要
KEEP_LATEST = 10
# 单条消息保留最大字符数
MAX_MSG_CHARS = 200


class ConversationManager:
    """管理多轮对话历史，持久化到 JSON 文件"""

    def __init__(self, max_history: int = 20):
        self.max_history = max_history
        CONV_DIR.mkdir(parents=True, exist_ok=True)

    # ---------------------------------------------------------------
    # 对话生命周期
    # ---------------------------------------------------------------

    def create_conversation(self, user_id: Optional[int] = None) -> str:
        """创建新对话，返回 conversation_id"""
        conv_id = uuid.uuid4().hex[:12]
        self._save(conv_id, {
            "id": conv_id,
            "user_id": user_id,
            "created_at": time.time(),
            "last_active": time.time(),
            "message_count": 0,
            "topic": "",
            "summary": "",           # 压缩后的摘要文本（给 LLM 看）
            "key_points": [],         # 提取的关键信息列表（结构化）
            "messages": [],
        })
        return conv_id

    def find_by_user(self, user_id: int) -> Optional[str]:
        """找到该用户最近活跃的对话 ID，没有则返回 None"""
        latest_id = None
        latest_time = 0.0
        for path in CONV_DIR.glob("*.json"):
            try:
                with open(path, "r", encoding="utf-8") as f:
                    data = json.load(f)
                if data.get("user_id") == user_id and data.get("last_active", 0) > latest_time:
                    latest_time = data["last_active"]
                    latest_id = data["id"]
            except Exception:
                continue
        return latest_id

    # ---------------------------------------------------------------
    # 消息管理
    # ---------------------------------------------------------------

    def add_message(self, conv_id: str, role: str, content: str) -> None:
        """添加一条消息，附带关键信息提取与自动摘要"""
        conv = self._load(conv_id)
        if not conv:
            conv = {
                "id": conv_id, "user_id": None, "created_at": time.time(),
                "last_active": time.time(), "message_count": 0, "topic": "",
                "summary": "", "key_points": [], "messages": [],
            }

        # 提取本条消息的关键信息（用户消息才提取）
        new_points = []
        if role == "user":
            new_points = self._extract_key_points(content)
        elif role == "assistant" and content:
            # AI 回复也提取关键词，比如单词、动作等
            new_points = self._extract_ai_points(content)

        # 合并关键信息（去重 + 留最新的）
        existing_texts = {p["text"] for p in conv.get("key_points", [])}
        for p in new_points:
            if p["text"] not in existing_texts:
                conv.setdefault("key_points", []).append(p)
                existing_texts.add(p["text"])
        # 只保留最近 30 条关键信息
        if len(conv.get("key_points", [])) > 30:
            conv["key_points"] = conv["key_points"][-30:]

        # 追加消息
        conv["messages"].append({
            "role": role,
            "content": content,
            "timestamp": time.time(),
        })
        conv["last_active"] = time.time()
        conv["message_count"] = conv.get("message_count", 0) + 1

        # 超出阈值时触发摘要
        if conv["message_count"] >= SUMMARY_THRESHOLD:
            self._compress(conv)

        # 截断旧消息（保留 max_history 条）
        if len(conv["messages"]) > self.max_history:
            conv["messages"] = conv["messages"][-self.max_history:]

        self._save(conv_id, conv)

    def get_history(self, conv_id: str, limit: int = 10) -> list[dict]:
        """获取最近 N 轮对话，附带摘要和关键信息"""
        conv = self._load(conv_id)
        if not conv:
            return []

        messages = conv["messages"][-limit:]

        # 前置摘要（文本形式）
        summary_parts = []
        if conv.get("summary"):
            summary_parts.append(f"以下是早期对话摘要:\n{conv['summary']}")

        # 前置关键信息（结构化形式）
        if conv.get("key_points"):
            lines = ["## 对话中已涉及的信息"]
            for p in conv["key_points"][-15:]:  # 只取最近 15 条
                lines.append(f"- {p['text']}")
            summary_parts.append("\n".join(lines))

        if summary_parts:
            messages.insert(0, {
                "role": "system",
                "content": "\n\n".join(summary_parts),
            })

        return messages

    def update_metadata(self, conv_id: str, **kwargs) -> None:
        """更新对话元数据（user_id, topic 等）"""
        conv = self._load(conv_id)
        if conv:
            for key in ("user_id", "topic"):
                if key in kwargs:
                    conv[key] = kwargs[key]
            conv["last_active"] = time.time()
            self._save(conv_id, conv)

    # ---------------------------------------------------------------
    # 摘要压缩
    # ---------------------------------------------------------------

    def _compress(self, conv: dict) -> None:
        """压缩早期对话：生成摘要 + 清理消息列表"""
        messages = conv.get("messages", [])
        if len(messages) <= KEEP_LATEST:
            return

        old = messages[:-KEEP_LATEST]   # 要被压缩的
        new_list = messages[-KEEP_LATEST:]  # 保留的

        # 按轮次分组（user + assistant 为一轮）
        rounds = []
        i = 0
        while i < len(old):
            turn = {"user": "", "assistant": ""}
            if old[i]["role"] == "user":
                turn["user"] = old[i]["content"]
                i += 1
                if i < len(old) and old[i]["role"] == "assistant":
                    turn["assistant"] = old[i]["content"]
                    i += 1
            else:
                turn["assistant"] = old[i]["content"]
                i += 1
            rounds.append(turn)

        # 生成摘要行
        summary_lines = []
        for turn in rounds[-8:]:  # 只取最后 8 轮
            user_msg = self._truncate_text(turn["user"], MAX_MSG_CHARS)
            if user_msg:
                summary_lines.append(f"用户: {user_msg}")
            ai_msg = self._truncate_text(turn["assistant"], MAX_MSG_CHARS)
            if ai_msg:
                summary_lines.append(f"AI: {ai_msg}")

        # 累计模式：已有摘要则追加，否则新建
        existing = conv.get("summary", "")
        if existing:
            # 截断旧摘要，合并新内容
            old_summary = self._truncate_text(existing, 500)
            new_summary = "\n".join(summary_lines)
            conv["summary"] = f"{old_summary}\n(续)\n{new_summary}"
            # 控制总长度
            if len(conv["summary"]) > 1000:
                conv["summary"] = conv["summary"][-1000:]
        else:
            conv["summary"] = "\n".join(summary_lines)

        conv["messages"] = new_list

    # ---------------------------------------------------------------
    # 关键信息提取
    # ---------------------------------------------------------------

    @staticmethod
    def _extract_key_points(content: str) -> list[dict]:
        """从用户消息中提取关键信息（查词、动作、偏好）"""
        points = []
        text = content.strip()

        if not text:
            return points

        # 提取英文单词（长词优先，去重）
        words = re.findall(r"\b[a-zA-Z]{3,}\b", text)
        meaningful = [w.lower() for w in words
                      if w.lower() not in _KEY_STOPWORDS]
        seen_words = set()
        for w in meaningful:
            if w not in seen_words and len(w) >= 3:
                seen_words.add(w)
                points.append({
                    "type": "word",
                    "text": f"查询/提到单词: {w}",
                    "timestamp": time.time(),
                })

        # 检测意图
        if any(kw in text for kw in ("签到", "checkin", "每日打卡")):
            points.append({
                "type": "action",
                "text": "执行签到操作",
                "timestamp": time.time(),
            })

        if any(kw in text for kw in ("积分", "余额", "points", "balance")):
            points.append({
                "type": "action",
                "text": "查询积分/余额",
                "timestamp": time.time(),
            })

        if any(kw in text for kw in ("推荐", "词书", "学什么", "背什么")):
            points.append({
                "type": "preference",
                "text": "请求学习推荐",
                "timestamp": time.time(),
            })

        # 检测长度，超过 100 字的消息可能是上传内容
        if len(text) > 100:
            title_match = re.search(r"^(.{4,30}?)[：:]", text)
            title = title_match.group(1) if title_match else text[:20]
            points.append({
                "type": "content",
                "text": f"上传/输入内容: {title}...",
                "timestamp": time.time(),
            })

        return points

    @staticmethod
    def _extract_ai_points(content: str) -> list[dict]:
        """从 AI 回复中提取关键信息"""
        points = []
        text = content.strip()
        if not text:
            return points

        # 提取 AI 回复中出现的单词（可能是在解释）
        words = re.findall(r"\*\*([a-zA-Z]{3,})\*\*", text)  # **beautiful** 格式
        for w in words[:5]:
            points.append({
                "type": "word",
                "text": f"解释单词: {w.lower()}",
                "timestamp": time.time(),
            })

        return points

    # ---------------------------------------------------------------
    # 过期清理
    # ---------------------------------------------------------------

    def clean_expired(self, max_age_days: int = 7) -> int:
        """清理超过 N 天未活跃的对话，返回清理数量"""
        now = time.time()
        cutoff = now - max_age_days * 86400
        cleaned = 0
        for path in CONV_DIR.glob("*.json"):
            try:
                with open(path, "r", encoding="utf-8") as f:
                    data = json.load(f)
                if data.get("last_active", 0) < cutoff:
                    path.unlink()
                    cleaned += 1
            except Exception:
                path.unlink()
                cleaned += 1
        if cleaned:
            from loguru import logger
            logger.info(f"清理了 {cleaned} 个过期对话")
        return cleaned

    def clear(self, conv_id: str) -> None:
        """清空对话历史"""
        path = CONV_DIR / f"{conv_id}.json"
        if path.exists():
            path.unlink()

    # ---------------------------------------------------------------
    # 内部工具
    # ---------------------------------------------------------------

    @staticmethod
    def _truncate_text(text: str, max_chars: int) -> str:
        """在句子边界截断，避免断开文字"""
        if len(text) <= max_chars:
            return text
        cut = text[:max_chars]
        # 在最近的句号处截断
        for sep in ("。", "！", "？", ". ", "! ", "? "):
            idx = cut.rfind(sep)
            if idx > max_chars // 2:
                return cut[:idx + len(sep)] + ".."
        return cut[:max_chars] + ".."

    def _load(self, conv_id: str) -> Optional[dict]:
        path = CONV_DIR / f"{conv_id}.json"
        if path.exists():
            try:
                with open(path, "r", encoding="utf-8") as f:
                    return json.load(f)
            except (json.JSONDecodeError, IOError):
                return None
        return None

    def _save(self, conv_id: str, data: dict) -> None:
        path = CONV_DIR / f"{conv_id}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)


# 关键信息提取用的停用词（避免把无意义的词记进去）
_KEY_STOPWORDS: set[str] = {
    "the", "is", "are", "was", "were", "has", "have", "had", "do",
    "does", "did", "will", "would", "could", "should", "may", "might",
    "can", "shall", "am", "be", "been", "being", "it", "its", "this",
    "that", "these", "those", "what", "which", "who", "whom", "whose",
    "when", "where", "why", "how", "a", "an", "and", "or", "but", "if",
    "because", "so", "than", "too", "very", "just", "about", "above",
    "after", "again", "all", "also", "any", "back", "each", "every",
    "for", "from", "get", "got", "here", "him", "his", "into", "let",
    "like", "make", "more", "most", "much", "must", "my", "no", "not",
    "now", "of", "on", "one", "only", "other", "our", "out", "over",
    "own", "say", "she", "some", "tell", "their", "them", "then",
    "there", "they", "thing", "things", "think", "through", "upon",
    "use", "used", "way", "well", "with", "without", "you", "your",
    "want", "know", "help", "test", "learn", "study", "recommend",
    "need", "give", "tell", "show", "check", "see", "look",
    "please", "hello", "hi", "hey", "thanks", "thank", "ok", "okay",
    "yes", "no", "sure", "sorry", "good", "great",
}
