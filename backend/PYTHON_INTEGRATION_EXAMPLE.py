"""
Python智能体调用Java后端API示例

本文件展示了如何从Python智能体项目调用词汇学习后端的API接口。

前置条件：
1. Java后端服务已启动（默认端口8080）
2. 已注册用户并获取token
3. 安装requests库：pip install requests
"""

import requests
import json
from typing import Dict, List, Optional


class VocabularyAPIClient:
    """词汇学习API客户端"""
    
    def __init__(self, base_url: str = "http://localhost:8080", token: str = None):
        """
        初始化API客户端
        
        Args:
            base_url: API基础URL
            token: 用户认证token（可选，部分接口需要）
        """
        self.base_url = base_url
        self.token = token
        self.session = requests.Session()
        
        # 如果提供了token，设置默认请求头
        if token:
            self.session.headers.update({
                'Authorization': f'Bearer {token}',
                'Content-Type': 'application/json'
            })
    
    def set_token(self, token: str):
        """设置认证token"""
        self.token = token
        self.session.headers.update({
            'Authorization': f'Bearer {token}',
            'Content-Type': 'application/json'
        })
    
    def _request(self, method: str, endpoint: str, data: Dict = None, params: Dict = None) -> Dict:
        """
        发送HTTP请求
        
        Args:
            method: HTTP方法 (GET, POST, PUT, DELETE)
            endpoint: API端点
            data: 请求体数据
            params: URL参数
            
        Returns:
            响应JSON数据
        """
        url = f"{self.base_url}{endpoint}"
        
        try:
            response = self.session.request(
                method=method,
                url=url,
                json=data,
                params=params
            )
            response.raise_for_status()
            return response.json()
        except requests.exceptions.RequestException as e:
            print(f"请求失败: {e}")
            if hasattr(e, 'response') and e.response is not None:
                print(f"错误响应: {e.response.text}")
            raise
    
    # ==================== 用户认证接口 ====================
    
    def register(self, username: str, password: str, nickname: str = None) -> Dict:
        """
        用户注册
        
        Args:
            username: 用户名
            password: 密码
            nickname: 昵称（可选）
            
        Returns:
            注册结果
        """
        data = {
            'username': username,
            'password': password
        }
        if nickname:
            data['nickname'] = nickname
        
        return self._request('POST', '/api/user/register', data=data)
    
    def login(self, username: str, password: str) -> Dict:
        """
        用户登录
        
        Args:
            username: 用户名
            password: 密码
            
        Returns:
            登录结果（包含token）
        """
        data = {
            'username': username,
            'password': password
        }
        
        result = self._request('POST', '/api/user/login', data=data)
        
        # 如果登录成功，自动设置token
        if result.get('code') == 200 and 'data' in result:
            token = result['data'].get('token')
            if token:
                self.set_token(token)
                print(f"登录成功，Token已设置")
        
        return result
    
    # ==================== 单词书接口 ====================
    
    def create_vocabulary_book(self, user_id: int, book_name: str, description: str = "") -> Dict:
        """
        创建单词书
        
        Args:
            user_id: 用户ID
            book_name: 单词书名称
            description: 描述（可选）
            
        Returns:
            创建结果
        """
        data = {
            'userId': user_id,
            'bookName': book_name,
            'description': description
        }
        
        return self._request('POST', '/api/vocabulary-book', data=data)
    
    def get_books_by_user(self, user_id: int) -> Dict:
        """
        查询用户的所有单词书
        
        Args:
            user_id: 用户ID
            
        Returns:
            单词书列表
        """
        return self._request('GET', f'/api/vocabulary-book/list/{user_id}')
    
    def get_book_detail(self, book_id: int) -> Dict:
        """
        查询单词书详情
        
        Args:
            book_id: 单词书ID
            
        Returns:
            单词书详情
        """
        return self._request('GET', f'/api/vocabulary-book/{book_id}')
    
    def update_book(self, book_id: int, book_name: str = None, 
                   description: str = None, is_public: int = None) -> Dict:
        """
        更新单词书
        
        Args:
            book_id: 单词书ID
            book_name: 新名称（可选）
            description: 新描述（可选）
            is_public: 是否公开 0-私有 1-公开（可选）
            
        Returns:
            更新结果
        """
        params = {}
        if book_name:
            params['bookName'] = book_name
        if description:
            params['description'] = description
        if is_public is not None:
            params['isPublic'] = is_public
        
        return self._request('PUT', f'/api/vocabulary-book/{book_id}', params=params)
    
    def delete_book(self, book_id: int) -> Dict:
        """
        删除单词书
        
        Args:
            book_id: 单词书ID
            
        Returns:
            删除结果
        """
        return self._request('DELETE', f'/api/vocabulary-book/{book_id}')
    
    # ==================== 单词管理接口 ====================
    
    def ai_fill_word(self, word_text: str) -> Dict:
        """
        使用AI填充单词信息
        
        Args:
            word_text: 单词文本
            
        Returns:
            单词详细信息
        """
        # 注意：这里需要发送纯字符串，不是JSON对象
        url = f"{self.base_url}/api/vocabulary-book/word/ai-fill"
        
        try:
            response = self.session.post(
                url,
                data=json.dumps(word_text),
                headers={'Content-Type': 'application/json'}
            )
            response.raise_for_status()
            return response.json()
        except requests.exceptions.RequestException as e:
            print(f"AI填充单词失败: {e}")
            raise
    
    def add_word_to_book(self, book_id: int, word_text: str, 
                        phonetic: str = "", part_of_speech: str = "",
                        definition: str = "", example_sentence: str = "",
                        example_translation: str = "") -> Dict:
        """
        添加单词到单词书
        
        Args:
            book_id: 单词书ID
            word_text: 单词文本
            phonetic: 音标
            part_of_speech: 词性
            definition: 释义
            example_sentence: 例句
            example_translation: 例句翻译
            
        Returns:
            添加结果
        """
        data = {
            'bookId': book_id,
            'wordText': word_text,
            'phonetic': phonetic,
            'partOfSpeech': part_of_speech,
            'definition': definition,
            'exampleSentence': example_sentence,
            'exampleTranslation': example_translation
        }
        
        return self._request('POST', '/api/vocabulary-book/add-word', data=data)
    
    def add_word_with_ai(self, book_id: int, word_text: str) -> Dict:
        """
        使用AI自动填充单词信息并添加到单词书
        
        Args:
            book_id: 单词书ID
            word_text: 单词文本
            
        Returns:
            添加结果
        """
        # 第一步：使用AI获取单词信息
        print(f"正在使用AI获取单词 '{word_text}' 的信息...")
        ai_result = self.ai_fill_word(word_text)
        
        if ai_result.get('code') != 200:
            raise Exception(f"AI填充失败: {ai_result.get('message')}")
        
        word_info = ai_result.get('data', {})
        
        # 第二步：添加到单词书
        print(f"正在将单词添加到单词书 {book_id}...")
        return self.add_word_to_book(
            book_id=book_id,
            word_text=word_info.get('wordText', word_text),
            phonetic=word_info.get('phonetic', ''),
            part_of_speech=word_info.get('partOfSpeech', ''),
            definition=word_info.get('definition', ''),
            example_sentence=word_info.get('exampleSentence', ''),
            example_translation=word_info.get('exampleTranslation', '')
        )


# ==================== 使用示例 ====================

def example_basic_usage():
    """基本使用示例"""
    
    # 1. 创建API客户端
    client = VocabularyAPIClient(base_url="http://localhost:8080")
    
    # 2. 用户注册（如果已有账号可跳过）
    print("=== 用户注册 ===")
    register_result = client.register(
        username="test_user",
        password="password123",
        nickname="测试用户"
    )
    print(f"注册结果: {register_result}")
    
    # 3. 用户登录
    print("\n=== 用户登录 ===")
    login_result = client.login(
        username="test_user",
        password="password123"
    )
    print(f"登录结果: {login_result}")
    
    # 4. 创建单词书
    print("\n=== 创建单词书 ===")
    user_id = 1  # 从登录结果中获取实际的用户ID
    create_result = client.create_vocabulary_book(
        user_id=user_id,
        book_name="我的生词本",
        description="日常学习的单词"
    )
    print(f"创建结果: {create_result}")
    
    # 5. 查询单词书列表
    print("\n=== 查询单词书列表 ===")
    books_result = client.get_books_by_user(user_id)
    print(f"单词书列表: {json.dumps(books_result, ensure_ascii=False, indent=2)}")
    
    # 6. 使用AI填充单词信息
    print("\n=== AI填充单词 ===")
    ai_result = client.ai_fill_word("snake")
    print(f"AI填充结果: {json.dumps(ai_result, ensure_ascii=False, indent=2)}")
    
    # 7. 添加单词到单词书
    print("\n=== 添加单词到单词书 ===")
    book_id = 1  # 从创建结果或列表中获取实际的单词书ID
    add_result = client.add_word_to_book(
        book_id=book_id,
        word_text="snake",
        phonetic="/sneɪk/",
        part_of_speech="n.",
        definition="蛇",
        example_sentence="The snake slithered through the grass.",
        example_translation="蛇在草丛中滑行。"
    )
    print(f"添加结果: {add_result}")
    
    # 8. 使用AI自动填充并添加（推荐方式）
    print("\n=== AI自动填充并添加 ===")
    auto_add_result = client.add_word_with_ai(book_id, "apple")
    print(f"自动添加结果: {auto_add_result}")


def example_smart_agent_integration():
    """智能体集成示例"""
    
    # 假设你有一个智能体在处理用户的单词学习需求
    
    class SmartLearningAgent:
        """智能学习助手"""
        
        def __init__(self, api_client: VocabularyAPIClient):
            self.client = api_client
            self.user_id = None
            self.current_book_id = None
        
        def initialize_user(self, username: str, password: str):
            """初始化用户"""
            login_result = self.client.login(username, password)
            if login_result.get('code') == 200:
                self.user_id = login_result['data'].get('userId')
                print(f"用户 {username} 初始化成功")
        
        def create_or_get_book(self, book_name: str) -> int:
            """创建或获取单词书ID"""
            # 先查询现有单词书
            books_result = self.client.get_books_by_user(self.user_id)
            books = books_result.get('data', [])
            
            # 查找是否已存在
            for book in books:
                if book['bookName'] == book_name:
                    self.current_book_id = book['id']
                    print(f"找到现有单词书: {book_name}, ID: {self.current_book_id}")
                    return self.current_book_id
            
            # 不存在则创建
            create_result = self.client.create_vocabulary_book(
                self.user_id, book_name
            )
            if create_result.get('code') == 200:
                # 重新查询获取ID
                books_result = self.client.get_books_by_user(self.user_id)
                books = books_result.get('data', [])
                for book in books:
                    if book['bookName'] == book_name:
                        self.current_book_id = book['id']
                        print(f"创建新单词书: {book_name}, ID: {self.current_book_id}")
                        return self.current_book_id
        
        def learn_word(self, word: str):
            """学习一个单词"""
            if not self.current_book_id:
                raise Exception("请先选择或创建单词书")
            
            print(f"\n📚 正在学习单词: {word}")
            
            try:
                # 使用AI自动获取单词信息并添加
                result = self.client.add_word_with_ai(
                    self.current_book_id, word
                )
                
                if result.get('code') == 200:
                    print(f"✅ 单词 '{word}' 已成功添加到单词书")
                else:
                    print(f"⚠️ 添加失败: {result.get('message')}")
                    
            except Exception as e:
                print(f"❌ 学习失败: {e}")
        
        def learn_batch_words(self, words: List[str]):
            """批量学习单词"""
            print(f"\n📖 开始批量学习 {len(words)} 个单词...")
            
            success_count = 0
            for word in words:
                try:
                    self.learn_word(word)
                    success_count += 1
                except Exception as e:
                    print(f"跳过单词 '{word}': {e}")
            
            print(f"\n批量学习完成: 成功 {success_count}/{len(words)} 个单词")
    
    # 使用智能体
    agent_client = VocabularyAPIClient(base_url="http://localhost:8080")
    agent = SmartLearningAgent(agent_client)
    
    # 初始化用户
    agent.initialize_user("test_user", "password123")
    
    # 创建单词书
    agent.create_or_get_book("英语学习计划")
    
    # 学习单个单词
    agent.learn_word("computer")
    
    # 批量学习
    agent.learn_batch_words([
        "programming",
        "algorithm",
        "database",
        "network"
    ])


if __name__ == "__main__":
    print("=" * 60)
    print("Python智能体调用Java后端API示例")
    print("=" * 60)
    
    # 运行基本示例
    try:
        example_basic_usage()
    except Exception as e:
        print(f"\n基本示例执行失败: {e}")
    
    print("\n" + "=" * 60)
    
    # 运行智能体集成示例
    try:
        example_smart_agent_integration()
    except Exception as e:
        print(f"\n智能体示例执行失败: {e}")
