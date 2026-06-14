"""
Python调用Java后端API - 快速入门示例

这个文件展示了最简单的调用方式，适合快速测试和集成。
"""

import requests
import json


# ==================== 配置 ====================
BASE_URL = "http://localhost:8080"
USERNAME = "test_user"
PASSWORD = "password123"


# ==================== 辅助函数 ====================

def get_headers(token=None):
    """获取请求头"""
    headers = {
        'Content-Type': 'application/json'
    }
    if token:
        headers['Authorization'] = f'Bearer {token}'
    return headers


def print_result(title, result):
    """打印结果"""
    print(f"\n{'='*50}")
    print(f"{title}")
    print('='*50)
    print(json.dumps(result, ensure_ascii=False, indent=2))


# ==================== API调用示例 ====================

def login():
    """登录获取token"""
    url = f"{BASE_URL}/api/user/login"
    data = {
        "username": USERNAME,
        "password": PASSWORD
    }
    
    response = requests.post(url, json=data, headers=get_headers())
    result = response.json()
    
    if result.get('code') == 200:
        token = result['data']['token']
        user_id = result['data']['userId']
        print_result("登录成功", result)
        return token, user_id
    else:
        raise Exception(f"登录失败: {result.get('message')}")


def create_book(token, user_id, book_name):
    """创建单词书"""
    url = f"{BASE_URL}/api/vocabulary-book"
    data = {
        "userId": user_id,
        "bookName": book_name,
        "description": "通过Python创建的单词书"
    }
    
    response = requests.post(url, json=data, headers=get_headers(token))
    result = response.json()
    print_result(f"创建单词书: {book_name}", result)
    return result


def get_books(token, user_id):
    """获取单词书列表"""
    url = f"{BASE_URL}/api/vocabulary-book/list/{user_id}"
    
    response = requests.get(url, headers=get_headers(token))
    result = response.json()
    print_result("单词书列表", result)
    return result


def ai_fill_word(token, word_text):
    """使用AI填充单词信息"""
    url = f"{BASE_URL}/api/vocabulary-book/word/ai-fill"
    
    # 注意：这里发送的是纯字符串，需要用json.dumps包装
    response = requests.post(
        url, 
        data=json.dumps(word_text),
        headers=get_headers(token)
    )
    result = response.json()
    print_result(f"AI填充单词: {word_text}", result)
    return result


def add_word_to_book(token, book_id, word_info):
    """添加单词到单词书"""
    url = f"{BASE_URL}/api/vocabulary-book/add-word"
    
    data = {
        "bookId": book_id,
        "wordText": word_info.get('wordText', ''),
        "phonetic": word_info.get('phonetic', ''),
        "partOfSpeech": word_info.get('partOfSpeech', ''),
        "definition": word_info.get('definition', ''),
        "exampleSentence": word_info.get('exampleSentence', ''),
        "exampleTranslation": word_info.get('exampleTranslation', '')
    }
    
    response = requests.post(url, json=data, headers=get_headers(token))
    result = response.json()
    print_result(f"添加单词: {word_info.get('wordText')}", result)
    return result


# ==================== 完整流程示例 ====================

def main():
    """完整的使用流程"""
    
    print("🚀 开始演示Python调用Java后端API")
    print("=" * 60)
    
    try:
        # 步骤1: 登录
        print("\n📝 步骤1: 用户登录")
        token, user_id = login()
        print(f"✓ Token: {token[:20]}...")
        print(f"✓ User ID: {user_id}")
        
        # 步骤2: 创建单词书
        print("\n📝 步骤2: 创建单词书")
        book_name = "Python智能体学习的单词"
        create_result = create_book(token, user_id, book_name)
        
        # 获取刚创建的单词书ID
        books_result = get_books(token, user_id)
        books = books_result.get('data', [])
        book_id = None
        for book in books:
            if book['bookName'] == book_name:
                book_id = book['id']
                break
        
        if not book_id:
            raise Exception("未找到刚创建的单词书")
        
        print(f"✓ 单词书ID: {book_id}")
        
        # 步骤3: 使用AI填充单词信息
        print("\n📝 步骤3: 使用AI获取单词信息")
        word_text = "python"
        ai_result = ai_fill_word(token, word_text)
        
        if ai_result.get('code') != 200:
            raise Exception(f"AI填充失败: {ai_result.get('message')}")
        
        word_info = ai_result.get('data', {})
        print(f"✓ 单词: {word_info.get('wordText')}")
        print(f"✓ 音标: {word_info.get('phonetic')}")
        print(f"✓ 释义: {word_info.get('definition')}")
        
        # 步骤4: 添加到单词书
        print("\n📝 步骤4: 添加单词到单词书")
        add_result = add_word_to_book(token, book_id, word_info)
        
        if add_result.get('code') == 200:
            print("✓ 单词添加成功！")
        else:
            print(f"⚠ 添加失败: {add_result.get('message')}")
        
        # 步骤5: 批量添加多个单词
        print("\n📝 步骤5: 批量添加单词")
        words = ["java", "spring", "database"]
        
        for word in words:
            try:
                print(f"\n  处理单词: {word}")
                ai_res = ai_fill_word(token, word)
                if ai_res.get('code') == 200:
                    word_data = ai_res.get('data', {})
                    add_res = add_word_to_book(token, book_id, word_data)
                    if add_res.get('code') == 200:
                        print(f"  ✓ {word} 添加成功")
                    else:
                        print(f"  ⚠ {word} 添加失败: {add_res.get('message')}")
                else:
                    print(f"  ⚠ {word} AI填充失败")
            except Exception as e:
                print(f"  ✗ {word} 处理异常: {e}")
        
        print("\n" + "=" * 60)
        print("✅ 演示完成！")
        print("=" * 60)
        
    except Exception as e:
        print(f"\n❌ 执行失败: {e}")
        import traceback
        traceback.print_exc()


# ==================== 简单调用示例（无错误处理）====================

def simple_example():
    """最简化的调用示例"""
    
    # 1. 登录
    login_resp = requests.post(
        f"{BASE_URL}/api/user/login",
        json={"username": USERNAME, "password": PASSWORD}
    )
    token = login_resp.json()['data']['token']
    user_id = login_resp.json()['data']['userId']
    
    # 2. 创建单词书
    requests.post(
        f"{BASE_URL}/api/vocabulary-book",
        json={"userId": user_id, "bookName": "测试单词书"},
        headers={'Authorization': f'Bearer {token}', 'Content-Type': 'application/json'}
    )
    
    # 3. AI填充单词
    ai_resp = requests.post(
        f"{BASE_URL}/api/vocabulary-book/word/ai-fill",
        data=json.dumps("hello"),
        headers={'Authorization': f'Bearer {token}', 'Content-Type': 'application/json'}
    )
    word_info = ai_resp.json()['data']
    
    # 4. 添加到单词书
    requests.post(
        f"{BASE_URL}/api/vocabulary-book/add-word",
        json={
            "bookId": 1,
            "wordText": word_info['wordText'],
            "definition": word_info['definition']
        },
        headers={'Authorization': f'Bearer {token}', 'Content-Type': 'application/json'}
    )
    
    print("简单示例执行完成！")


if __name__ == "__main__":
    # 运行完整示例
    main()
    
    # 或者运行简单示例
    # simple_example()
