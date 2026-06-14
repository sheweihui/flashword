"""后端 API 通用 HTTP 客户端"""

from typing import Optional, Any

import requests
from loguru import logger

from config.settings import API_BASE_URL, API_TIMEOUT
from .auth import AuthManager


class ApiError(Exception):
    """API 调用异常"""
    def __init__(self, message: str, code: int = -1, data: Any = None):
        super().__init__(message)
        self.code = code
        self.data = data


class ApiClient:
    """封装对后端 REST API 的 HTTP 请求"""

    def __init__(self, base_url: str = API_BASE_URL, timeout: int = API_TIMEOUT):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.auth = AuthManager()
        self._session = requests.Session()
        self._session.headers.update({"Content-Type": "application/json"})

    # ---- 请求方法 ----

    def request(
        self,
        method: str,
        path: str,
        params: Optional[dict] = None,
        data: Optional[dict] = None,
        need_auth: bool = True,
    ) -> Any:
        url = f"{self.base_url}{path}"
        headers = {}

        if need_auth:
            token = self.auth.get_token()
            if token:
                headers["Authorization"] = f"Bearer {token}"

        logger.debug(f"{method} {url} | params={params} | body={data}")

        try:
            resp = self._session.request(
                method=method,
                url=url,
                params=params,
                json=data,
                headers=headers,
                timeout=self.timeout,
            )
        except requests.exceptions.ConnectionError:
            raise ApiError(f"无法连接到后端: {url}")
        except requests.exceptions.Timeout:
            raise ApiError(f"请求超时 ({self.timeout}s): {method} {path}")

        if resp.status_code == 401:
            self.auth.clear()
            raise ApiError("登录已过期，请重新登录", code=401)

        if resp.status_code != 200:
            raise ApiError(f"HTTP {resp.status_code}: {resp.text}", code=resp.status_code)

        body = resp.json()
        # 后端统一返回 {code, message, data}
        if body.get("code") == 200:
            return body.get("data")
        raise ApiError(body.get("message", "未知错误"), code=body.get("code", -1), data=body)

    def get(self, path: str, params: Optional[dict] = None, **kwargs) -> Any:
        return self.request("GET", path, params=params, **kwargs)

    def post(self, path: str, data: Optional[dict] = None, **kwargs) -> Any:
        return self.request("POST", path, data=data, **kwargs)

    def put(self, path: str, data: Optional[dict] = None, **kwargs) -> Any:
        return self.request("PUT", path, data=data, **kwargs)

    def delete(self, path: str, **kwargs) -> Any:
        return self.request("DELETE", path, **kwargs)

    # ---- 认证 ----

    def login(self, username: str, password: str) -> dict:
        from .auth import AuthSession
        result = self.post("/user/login", {"username": username, "password": password}, need_auth=False)
        # 后端返回 {code, message, data: {token, userId, username, nickname}}
        if isinstance(result, dict):
            session = AuthSession(
                token=result.get("token", ""),
                user_id=result.get("userId", 0),
                username=username,
                nickname=result.get("nickname", username),
            )
            self.auth.save_session(session)
            logger.info(f"登录成功: {username}")
        else:
            logger.warning(f"登录返回格式异常: {type(result)}")
        return result

    def register(self, username: str, password: str, nickname: str = "") -> dict:
        data = {"username": username, "password": password}
        if nickname:
            data["nickname"] = nickname
        return self.post("/user/register", data, need_auth=False)
