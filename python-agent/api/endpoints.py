"""业务 API 端点封装（基于通用 client）"""

from typing import Optional
from .client import ApiClient


class Endpoints:
    """封装各业务模块的 API 调用"""

    def __init__(self, client: ApiClient):
        self._c = client

    # ---- 用户 ----
    def get_user_info(self, user_id: int) -> dict:
        return self._c.get(f"/user/{user_id}")

    # ---- 单词 ----
    def search_word(self, keyword: str) -> list[dict]:
        return self._c.get("/word/search", params={"keyword": keyword})

    def search_my_word(self, keyword: str) -> list[dict]:
        """搜索当前用户个人单词本中的单词"""
        return self._c.get("/word/my/search", params={"keyword": keyword})

    def get_word(self, word_id: int) -> dict:
        return self._c.get(f"/word/{word_id}")

    def ai_fill_word(self, word_text: str) -> dict:
        return self._c.post("/word/ai-fill", data={"wordText": word_text})

    # ---- 单词本 ----
    def get_book_list(self, user_id: int) -> list[dict]:
        return self._c.get(f"/vocabulary-book/list/{user_id}")

    def get_book_by_id(self, book_id: int) -> dict:
        return self._c.get(f"/vocabulary-book/{book_id}")

    def create_book(self, user_id: int, name: str, description: str = "") -> dict:
        return self._c.post("/vocabulary-book", data={
            "userId": user_id, "bookName": name, "description": description
        })

    def get_words_by_book(self, book_id: int) -> list[dict]:
        return self._c.get("/vocabulary-book/words", params={"bookId": book_id})

    # ---- 商店 ----
    def get_points_balance(self) -> dict:
        return self._c.get("/store/points/balance")

    def checkin(self) -> dict:
        return self._c.post("/store/checkin")

    def get_store_books(self, page: int = 1, size: int = 10) -> list[dict]:
        return self._c.get("/store/books", params={"page": page, "size": size})

    def get_flash_sale_list(self) -> list[dict]:
        return self._c.get("/store/flash-sale/list")

    def purchase_flash_sale(self, activity_id: int) -> dict:
        return self._c.post(f"/store/flash-sale/purchase/{activity_id}")
