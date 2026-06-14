"""
工具结果格式化 — 供 tools.py 和 mcp_server.py 共享使用

避免两处维护相同的格式化逻辑。
"""


def format_word_result(words) -> str:
    """格式化单词查询结果"""
    if isinstance(words, list) and words:
        return format_single_word(words[0])
    if isinstance(words, dict):
        return format_single_word(words)
    return "未找到该单词信息。"


def format_single_word(w: dict) -> str:
    """格式化单个单词信息"""
    parts = []
    if w.get("wordText"):
        parts.append(f"单词: {w['wordText']}")
    if w.get("phonetic"):
        parts.append(f"音标: {w['phonetic']}")
    if w.get("partOfSpeech"):
        parts.append(f"词性: {w['partOfSpeech']}")
    if w.get("definition"):
        parts.append(f"释义: {w['definition']}")
    if w.get("exampleSentence"):
        parts.append(f"例句: {w['exampleSentence']}")
    if w.get("exampleTranslation"):
        parts.append(f"翻译: {w['exampleTranslation']}")
    return " | ".join(parts)


def format_flash_sales(sales) -> str:
    """格式化秒杀活动列表"""
    if not sales:
        return "当前没有秒杀活动"
    lines = ["秒杀活动列表:"]
    for s in sales:
        name = s.get("name", "")
        price = s.get("price", 0)
        stock = s.get("stock", 0)
        lines.append(f"  [{s.get('id')}] {name} — ¥{price} | 剩余: {stock}")
    return "\n".join(lines)


def format_book_list(books) -> str:
    """格式化单词书列表"""
    if not books:
        return "暂无数据"
    if isinstance(books, dict) and "records" in books:
        books = books["records"]
    lines = ["单词书列表:"]
    for b in books[:10]:
        name = b.get("bookName") or b.get("name", "")
        price = b.get("price", "免费")
        lines.append(f"  [{b.get('id')}] {name} | ¥{price}")
    return "\n".join(lines)


def format_word_list(words) -> str:
    """格式化单词列表"""
    if not words:
        return "该单词本中没有单词"
    lines = [f"共 {len(words)} 个单词:"]
    for w in words[:20]:
        text = w.get("wordText", "?")
        definition = w.get("definition", "")
        preview = (definition[:50] + "...") if len(definition) > 50 else (definition or "")
        lines.append(f"  {text} — {preview}")
    return "\n".join(lines)
