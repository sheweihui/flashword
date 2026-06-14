"""
上下文优化器：对 RAG 检索结果做压缩、排序、去噪

职责：
  - 按 Markdown section 解析上下文
  - 基于用户消息计算关键词相关性
  - 按优先级 + 相关性排序
  - 在 token 预算内裁剪
  - 过滤噪音（过短/无实质内容）

使用场景：
    optimizer = ContextOptimizer()
    optimized = optimizer.optimize(raw_context, user_message="beautiful 什么意思",
                                   max_tokens=1500, mode="balanced")

这是 RAG 工程中的关键一环 —— 原始检索结果往往包含冗余或低价值内容，
直接塞给 LLM 既浪费 token 又可能分散注意力。
"""

import re
import math
from typing import Optional

from loguru import logger

# 各类型上下文的基础优先级（数字越大越优先）
_SECTION_PRIORITY: dict[str, int] = {
    "## 拼写提示":      100,   # 用户拼错了，必须展示
    "## 单词查询":      90,    # 直接的查词结果
    "## 单词语义分组":  80,    # 复习请求的专属上下文
    "## 知识库匹配":    70,    # 用户上传的个人知识
    "## 用户学习概况":  60,    # 个性化数据（积分/词本）
    "## 我的单词本":    60,    # 同上
    "## 近义词推荐":    50,    # 扩展学习，非必需
    "## 学习推荐":      40,    # 推荐系统输出
    "## 词书推荐":      40,
}

# 中文停用词（用于关键词提取）
_ZH_STOPWORDS: set[str] = {
    "的", "了", "是", "在", "有", "吗", "呢", "吧", "啊", "哦",
    "我", "你", "他", "她", "它", "们", "这", "那", "什么",
    "怎么", "如何", "一个", "可以", "要", "会", "能", "想",
    "让", "给", "对", "把", "被", "和", "与", "就", "也", "都",
    "还", "很", "太", "更", "最", "不", "没", "别", "看", "好",
}

# Token 估算系数（中英混合，保守值）
_CHARS_PER_TOKEN = 2.5


class ContextOptimizer:
    """
    RAG 上下文优化器

    处理流程：
        1. parse — 按 ## 标题分割为独立段落
        2. filter — 移除噪音片段
        3. score  — 计算优先级 + 关键词相关性
        4. sort   — 高价值内容在前
        5. trim   — 在 token 预算内裁剪
        6. rebuild— 重新组装为 Markdown 文本

    三种模式：
        - full:      保留全部，仅排序（适合预算充足）
        - balanced:  优先保留高优先级，中低优先级按预算保留（默认）
        - aggressive:仅保留高优先级（>=80），适合长对话或小窗口模型
    """

    def optimize(
        self,
        context: str,
        user_message: str = "",
        max_tokens: int = 2000,
        mode: str = "balanced",
    ) -> str:
        """优化入口：解析 → 过滤 → 排序 → 裁剪 → 重组"""
        if not context or not context.strip():
            return ""

        raw_sections = self._parse_sections(context)
        if not raw_sections:
            return ""

        # 提取关键词
        keywords = self._extract_keywords(user_message) if user_message else set()
        if keywords:
            logger.info(f"[优化器] 用户关键词: {keywords}")

        # 为每个段落计算元数据
        sections = []
        for sec in raw_sections:
            sections.append({
                "title":   sec["title"],
                "text":    sec["text"],
                "priority": self._get_priority(sec["title"]),
                "relevance": self._calc_relevance(sec["text"], keywords),
                "tokens":   self._estimate_tokens(sec["text"]),
            })

        logger.info(f"[优化器] 原始: {len(sections)} 段, "
                     f"{sum(s['tokens'] for s in sections)} tokens")

        # 1. 过滤噪音
        before = len(sections)
        sections = self._filter_noise(sections)
        removed_noise = before - len(sections)
        if removed_noise:
            logger.info(f"[优化器] 噪音过滤: 移除 {removed_noise} 段")

        if not sections:
            return ""

        # 2. 综合排序：优先级降序 → 相关性降序
        sections.sort(key=lambda s: (s["priority"], s["relevance"]), reverse=True)
        order_preview = [f"{s['title']}(p={s['priority']},r={s['relevance']:.1f})" for s in sections]
        logger.info(f"[优化器] 排序后: {' > '.join(order_preview)}")

        # 3. 裁剪到预算
        before_tokens = sum(s['tokens'] for s in sections)
        sections = self._trim_to_budget(sections, max_tokens, mode)
        after_tokens = sum(s['tokens'] for s in sections) if sections else 0
        logger.info(f"[优化器] 裁剪: {before_tokens} → {after_tokens} tokens "
                     f"(模式={mode}, 预算={max_tokens})")

        if not sections:
            return ""

        # 4. 重组
        return "\n\n".join(s["text"] for s in sections)

    # ---------------------------------------------------------------
    # 段落解析
    # ---------------------------------------------------------------

    @staticmethod
    def _parse_sections(context: str) -> list[dict]:
        """按 ``## `` 标题将上下文拆分为独立段落"""
        lines = context.split("\n")
        sections: list[dict] = []
        current_title = ""
        current_lines: list[str] = []

        for line in lines:
            if line.startswith("## "):
                if current_lines:
                    sections.append({
                        "title": current_title,
                        "text": "\n".join(current_lines),
                    })
                current_title = line.strip()
                current_lines = [line]
            else:
                current_lines.append(line)

        if current_lines:
            sections.append({
                "title": current_title,
                "text": "\n".join(current_lines),
            })

        return sections

    # ---------------------------------------------------------------
    # 噪音过滤
    # ---------------------------------------------------------------

    @staticmethod
    def _filter_noise(sections: list[dict]) -> list[dict]:
        """移除过短或无实质内容的段落"""
        cleaned = []
        for sec in sections:
            text = sec["text"].strip()
            # 跳过纯空白或只有标题（长度 < 15 字符）
            if len(text) < 15:
                continue
            # 跳过不包含任何中英文或数字的段落
            if not re.search(r'[一-鿿\w]', text):
                continue
            cleaned.append(sec)
        return cleaned

    # ---------------------------------------------------------------
    # 优先级
    # ---------------------------------------------------------------

    @staticmethod
    def _get_priority(title: str) -> int:
        """根据标题确定优先级，未知标题按 30 分处理"""
        return _SECTION_PRIORITY.get(title, 30)

    # ---------------------------------------------------------------
    # 关键词相关性
    # ---------------------------------------------------------------

    @staticmethod
    def _calc_relevance(text: str, keywords: set[str]) -> float:
        """计算文本与关键词集合的相关度 [0, 1]"""
        if not keywords:
            return 0.0
        text_lower = text.lower()
        matches = sum(1 for kw in keywords if kw in text_lower)
        return matches / len(keywords)

    @staticmethod
    def _extract_keywords(message: str) -> set[str]:
        """从用户消息中提取中英文关键词（去停用词）"""
        # 英文单词
        en_words = re.findall(r"\b[a-zA-Z]{2,}\b", message.lower())
        # 中文词语（单字过滤，双字以上保留）
        cn_words = re.findall(r"[一-鿿]{2,}", message)
        cn_words = [w.lower() for w in cn_words]

        all_words = set(en_words + cn_words)
        return {w for w in all_words if w not in _ZH_STOPWORDS and len(w) > 1}

    # ---------------------------------------------------------------
    # Token 估算
    # ---------------------------------------------------------------

    @staticmethod
    def _estimate_tokens(text: str) -> int:
        """估算文本的 token 数（中英混合，保守估计）"""
        if not text:
            return 0
        return math.ceil(len(text) / _CHARS_PER_TOKEN)

    # ---------------------------------------------------------------
    # 预算裁剪
    # ---------------------------------------------------------------

    def _trim_to_budget(
        self,
        sections: list[dict],
        max_tokens: int,
        mode: str,
    ) -> list[dict]:
        """在 token 预算内保留最有价值的段落"""
        if not sections:
            return []

        if mode == "full":
            return sections

        result: list[dict] = []
        budget = max_tokens

        for sec in sections:
            tokens = sec["tokens"]

            # Aggressive 模式：跳过中低优先级
            if mode == "aggressive" and sec["priority"] < 80:
                continue

            # 这段超预算，尝试截断
            if tokens > budget:
                if sec["priority"] >= 80:
                    truncated = self._truncate_section(sec, budget)
                    if truncated:
                        result.append(truncated)
                continue

            result.append(sec)
            budget -= tokens

        return result

    @staticmethod
    def _truncate_section(section: dict, budget: int) -> Optional[dict]:
        """在预算内截断段落，保留标题 + 尽可能多的内容"""
        title = section.get("title", "")
        body = section["text"]

        # 标题本身可能已经超预算
        title_tokens = math.ceil(len(title) / _CHARS_PER_TOKEN)
        if title_tokens >= budget:
            return None

        # 从正文尾部截断
        available_chars = int((budget - title_tokens) * _CHARS_PER_TOKEN)
        if available_chars <= 0:
            return None

        truncated_body = body[:available_chars]
        # 在最后一个完整行处截断，避免断开行
        last_newline = truncated_body.rfind("\n")
        if last_newline > len(title):
            truncated_body = truncated_body[:last_newline]

        return {
            "title":    title,
            "text":     truncated_body,
            "priority": section["priority"],
            "relevance": section["relevance"],
            "tokens":   math.ceil(len(truncated_body) / _CHARS_PER_TOKEN),
        }

    # ---------------------------------------------------------------
    # 统计信息（供日志/监控使用）
    # ---------------------------------------------------------------

    def get_stats(self, before: str, after: str) -> dict:
        """返回优化前后的对比统计"""
        before_tokens = self._estimate_tokens(before)
        after_tokens = self._estimate_tokens(after)
        before_sections = len(self._parse_sections(before))
        after_sections = len(self._parse_sections(after))

        return {
            "before_tokens":    before_tokens,
            "after_tokens":     after_tokens,
            "before_sections":  before_sections,
            "after_sections":   after_sections,
            "compression_ratio": round(
                1 - after_tokens / max(before_tokens, 1), 3
            ),
            "sections_removed": before_sections - after_sections,
        }
