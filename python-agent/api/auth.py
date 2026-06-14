"""认证管理：登录、Token 存储与刷新"""

import os
import json
from pathlib import Path
from dataclasses import dataclass, asdict
from typing import Optional

TOKEN_FILE = Path(__file__).resolve().parent.parent / "data" / "auth.json"


@dataclass
class AuthSession:
    token: str
    user_id: int
    username: str
    nickname: Optional[str] = None


class AuthManager:
    """管理登录凭证的本地持久化"""

    def __init__(self):
        self._session: Optional[AuthSession] = None
        self._load()

    def save_session(self, session: AuthSession) -> None:
        self._session = session
        TOKEN_FILE.parent.mkdir(parents=True, exist_ok=True)
        with open(TOKEN_FILE, "w", encoding="utf-8") as f:
            json.dump(asdict(session), f, ensure_ascii=False, indent=2)

    def load_session(self) -> Optional[AuthSession]:
        return self._session

    def get_token(self) -> Optional[str]:
        return self._session.token if self._session else None

    def get_user_id(self) -> Optional[int]:
        return self._session.user_id if self._session else None

    def set_token(self, token: str, user_id: int = 0, username: str = "") -> None:
        """动态设置 token（从请求中传入，不持久化到文件）"""
        self._session = AuthSession(
            token=token,
            user_id=user_id,
            username=username or f"user_{user_id}",
        )

    def clear(self) -> None:
        self._session = None
        if TOKEN_FILE.exists():
            TOKEN_FILE.unlink()

    def _load(self) -> None:
        if TOKEN_FILE.exists():
            try:
                with open(TOKEN_FILE, "r", encoding="utf-8") as f:
                    data = json.load(f)
                self._session = AuthSession(**data)
            except (json.JSONDecodeError, KeyError):
                pass
