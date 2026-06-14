"""查询处理器 — 意图分类、查询改写、中英文关键词提取。

用法：
    qp = QueryProcessor()
    result = qp.process("beautiful什么意思，帮我签到")

    print(result.intent)       # ["word_query", "checkin"]
    print(result.keywords)     # ["beautiful", "签到"]
    print(result.rewritten)    # ["beautiful 释义", "beautiful 例句"]
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Set


# 意图关键词映射
_INTENT_PATTERNS: Dict[str, List[str]] = {
    "word_query": [
        "意思", "什么", "翻译", "怎么读", "发音", "单词", "含义",
        "释义", "例句", "音标", "词性", "解释",
    ],
    "checkin": [
        "签到", "checkin", "打卡", "每日签到",
    ],
    "points_query": [
        "积分", "points", "余额", "分数", "多少分",
    ],
    "book_query": [
        "单词本", "词书", "book", "我的词", "词汇本",
    ],
    "flash_sale": [
        "秒杀", "flash", "抢购", "活动",
    ],
    "recommend": [
        "推荐", "学什么", "背什么", "建议", "方法", "技巧", "怎么学",
    ],
    "quiz": [
        "测试", "题目", "考考", "出题", "测验",
    ],
    "knowledge_base": [
        "我的文档", "知识库", "上传", "笔记",
    ],
}

# 英文停用词（扩展）
_EN_STOPWORDS: Set[str] = {
    "the", "a", "an", "is", "are", "was", "were", "be", "been", "have",
    "has", "had", "do", "does", "did", "will", "would", "shall", "should",
    "may", "might", "can", "could", "must", "ought", "i", "you", "he",
    "she", "it", "we", "they", "my", "your", "his", "her", "its", "our",
    "their", "me", "him", "us", "them", "this", "that", "these", "those",
    "in", "on", "at", "to", "for", "with", "by", "from", "of", "and",
    "or", "not", "no", "but", "if", "so", "as", "than", "then", "also",
    "very", "just", "about", "want", "know", "help", "please", "hello",
    "hi", "hey", "thanks", "thank", "ok", "okay", "yes", "sure", "sorry",
    "good", "great", "see", "look", "need", "tell", "say",
}

# 中文停用词
_ZH_STOPWORDS: Set[str] = {
    "的", "了", "是", "在", "有", "吗", "呢", "吧", "啊", "哦", "嗯",
    "我", "你", "他", "她", "它", "们", "这", "那", "什么", "怎么",
    "如何", "一个", "可以", "要", "会", "能", "想", "让", "给", "对",
    "把", "被", "和", "与", "就", "也", "都", "还", "很", "太", "更",
    "最", "不", "没", "别", "看", "好", "请", "帮", "一下",
}


@dataclass
class ProcessedQuery:
    """查询处理结果。"""
    original: str = ""
    intent: List[str] = field(default_factory=list)
    keywords: List[str] = field(default_factory=list)
    rewritten: List[str] = field(default_factory=list)
    english_words: List[str] = field(default_factory=list)
    filters: Dict[str, str] = field(default_factory=dict)


class QueryProcessor:
    """查询处理器。

    对用户输入进行意图分类、关键词提取和查询改写。
    """

    def __init__(
        self,
        en_stopwords: Optional[Set[str]] = None,
        zh_stopwords: Optional[Set[str]] = None,
    ):
        self._en_stopwords = en_stopwords or _EN_STOPWORDS
        self._zh_stopwords = zh_stopwords or _ZH_STOPWORDS

    def process(self, message: str) -> ProcessedQuery:
        """处理用户查询。

        Args:
            message: 用户原始输入。

        Returns:
            ProcessedQuery 包含意图、关键词、改写结果。
        """
        result = ProcessedQuery(original=message)

        if not message.strip():
            return result

        # 1. 意图分类
        result.intent = self._classify_intent(message)

        # 2. 关键词提取（中英文）
        keywords, en_words = self._extract_keywords(message)
        result.keywords = keywords
        result.english_words = en_words

        # 3. 查询改写
        result.rewritten = self._rewrite_query(message, result.intent, en_words)

        # 4. 过滤器提取
        result.filters = self._extract_filters(message)

        return result

    def _classify_intent(self, message: str) -> List[str]:
        """对用户消息进行意图分类。"""
        text = message.lower()
        intents = []

        for intent, patterns in _INTENT_PATTERNS.items():
            for p in patterns:
                if p.lower() in text:
                    intents.append(intent)
                    break

        return intents

    def _extract_keywords(self, message: str) -> tuple[List[str], List[str]]:
        """提取关键词，返回 (全部关键词, 英文单词)。"""
        import jieba

        keywords: List[str] = []
        en_words: List[str] = []
        seen: Set[str] = set()

        # 英文单词
        for w in re.findall(r"[a-zA-Z]+", message):
            wl = w.lower()
            if wl not in self._en_stopwords and len(wl) >= 2 and wl not in seen:
                seen.add(wl)
                keywords.append(w)
                en_words.append(w)

        # 中文分词
        for w in jieba.lcut(message):
            w = w.strip()
            if w and w not in self._zh_stopwords and len(w) >= 2 and w not in seen:
                seen.add(w)
                keywords.append(w)

        return keywords[:20], en_words[:5]

    def _rewrite_query(self, message: str, intents: List[str], words: List[str]) -> List[str]:
        """生成改写后的查询列表。

        对查词意图，生成 "word 释义" "word 例句" 等变体。
        """
        rewritten = []

        if "word_query" in intents and words:
            for w in words[:2]:
                rewritten.append(f"{w} 释义")
                rewritten.append(f"{w} 例句")

        if not rewritten:
            rewritten.append(message)

        return rewritten

    @staticmethod
    def _extract_filters(message: str) -> Dict[str, str]:
        """提取 key:value 语法过滤器。"""
        filters = {}
        for m in re.finditer(r'(\w+):(\S+)', message):
            key, value = m.group(1).lower(), m.group(2)
            if key in ("collection", "col", "c"):
                filters["collection"] = value
        return filters
