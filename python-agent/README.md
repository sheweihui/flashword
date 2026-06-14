# 🧠 背单词助手 AI Agent

> 一个基于 FastAPI + DeepSeek 的智能背单词助手，支持自然语言对话、RAG 检索、Function Calling 工具调用。

---

## 📖 这个项目是做什么的？

**一句话**：让你的背单词 App 拥有一个 AI 聊天助手，可以用大白话查词、签到、看积分。

**举个例子**：

| 你说 | 它做什么 | 背后调用了什么 |
|---|---|---|
| "beautiful 是什么意思？" | 查词典 + 在你的词本里搜 | `GET /api/word/search` |
| "帮我签到" | 每日签到 | `POST /api/store/checkin` |
| "看看我多少积分" | 查积分余额 | `GET /api/store/points/balance` |
| "四级词本里有什么词？" | 列出词本内容 | `GET /api/vocabulary-book/words` |
| "商店有什么秒杀？" | 查看秒杀活动 | `GET /api/store/flash-sale/list` |

**区别**：不需要记命令格式，像跟人说话一样自然就行。

---

## 🏗️ 项目长什么样

```
flashword/
│
├── server.py                 ← 🔥 主入口，启动后就是一个 Web 服务
├── main.py                   ← 命令行模式（旧版，功能少）
├── .env                      ← 配置文件（API Key、端口等）
│
├── api/                      ← 🌉 跟后端 Java 通信的"电话线"
│   ├── client.py             ←   打 HTTP 电话的工具
│   ├── endpoints.py          ←   电话本（查词、签到、积分...）
│   ├── auth.py               ←   登录凭证（Token 管理）
│   └── schemas.py            ←   通话内容的格式约定
│
├── agent/                    ← 🧠 AI 大脑
│   ├── llm.py                ←   连接 DeepSeek 大模型
│   ├── tools.py              ←   工具箱（注册/执行工具）
│   ├── rag.py                ←   检索器（查数据库取上下文）
│   ├── conversation.py       ←   对话记忆（存聊天记录）
│   ├── core.py               ←   旧版大脑（已废弃）
│   └── memory.py             ←   简单记忆（记个用户名啥的）
│
├── config/
│   └── settings.py           ← 统一读取 .env 配置
│
└── data/                     ← 运行时数据
    ├── conversations/        ←   聊天记录（一个对话一个 JSON）
    ├── auth.json             ←   登录凭证缓存
    └── agent.log             ←   运行日志
```

---

## ⚙️ 怎么启动

### 前提：后端 Java 服务已经在跑（:8080）

### 启动 Agent：

```bash
cd flashword
.venv/Scripts/python.exe server.py
```

看到下面的输出就成功了：

```
INFO  | 启动 背单词助手 v0.2.0 → http://0.0.0.0:8000
INFO  | 后端连通性检查: OK (HTTP 200)
INFO  | LLM 已初始化 (DeepSeek)
```

### 测试是否启动成功：

浏览器打开：http://localhost:8000/agent/health

能看到：

```json
{
  "status": "ok",
  "version": "0.2.0",
  "llm_ready": true,
  "rag_cache_size": 0
}
```

### 发一条聊天消息：

```bash
curl -X POST http://localhost:8000/agent/chat ^
  -H "Content-Type: application/json" ^
  -d "{\"message\":\"hello\"}"
```

---

## 🔄 一条消息的完整旅程

你用聊天界面发了一句 **"beautiful是什么意思"**，背后发生了这些事：

```
你 → [HTTP] → Agent 服务 → RAG 检索 → 调 DeepSeek → 返回给你
                                      ↓
                                 可能需要调后端 Java
```

### 分步图解：

```
第 1 步：你发消息
  POST /agent/chat  {"message": "beautiful是什么意思", "user_id": 1}
  
第 2 步：Agent 保存消息
  把 "beautiful是什么意思" 存到 data/conversations/xxx.json

第 3 步：RAG 检索（同时查 3 个地方）
  ┌─ 公共词库查 "beautiful" ─→  GET /api/word/search
  ├─ 你的词本查 "beautiful" ─→  GET /api/word/my/search
  └─ 你的学习概况 ──────────→  GET /api/store/points/balance
                               GET /api/user/1
                               GET /api/vocabulary-book/list/1

第 4 步：把查到的信息拼成上下文
  "## 单词查询
   单词: beautiful | 音标: /ˈbjuːtɪfl/ | 释义: 美丽的
   ## 用户学习概况
   积分: 1250 | 单词本: 3 本"

第 5 步：发给 DeepSeek（大模型）
  系统消息：(刚才拼的上下文 + 工具列表)
  用户消息：beautiful是什么意思

第 6 步：DeepSeek 决定怎么做
  → 上下文已经有答案了 → 直接回复
  → 或者决定调工具查更多 → 返回 tool_call

第 7 步：回复返回给你
  "「beautiful」是形容词，意思是「美丽的」，
   音标 /ˈbjuːtɪfl/，你在四级词本里也收录了这个词哦！"
```

---

## 🧩 核心概念解释

### 1. RAG（检索增强生成）

**大白话**：在回答你之前，先去数据库把相关信息查出来，再让 AI 基于这些信息回答。

**没有 RAG 的情况**：
```
你：beautiful是什么意思
AI：beautiful 是"美丽的"（AI 自己知道，但不知道你的学习情况）
```

**有 RAG 的情况**：
```
你：beautiful是什么意思
    ↓ RAG 去后端查你的数据
AI：beautiful 是"美丽的"，这个词在你的四级词本里，
    你添加的笔记是"常考形容词"（AI 结合了你的个人数据）
```

### 2. Function Calling（函数调用）

**大白话**：AI 跟你聊天时，如果需要执行某个操作（签到、查积分），它自己决定调用哪个函数，然后执行完把结果告诉你。

```
你：帮我签到
    ↓
AI：好的，我来帮你签到（调用签到函数）
    ↓
签到函数执行 → 后端 Java 真正完成签到
    ↓
AI：签到成功！获得 10 积分，已连续签到 3 天
```

**注册一个新工具的代码**（在 `agent/tools.py` 里加 5 行）：

```python
Tool(
    name="purchase_book",              # 工具名
    description="购买单词书",           # 告诉 AI 这个工具是干什么的
    fn=api.purchase_book,              # 实际执行的方法
    parameters={"book_id": "词书 ID"}, # 需要的参数
)
```

### 3. LLM（大语言模型）

用的是 **DeepSeek**（兼容 OpenAI 接口）。如果没配置 API Key，系统会自动降级为**规则匹配模式**（用 if-else 回复），功能少但不会崩溃。

---

## 📡 API 接口

| 接口 | 方法 | 作用 |
|---|---|---|
| `/agent/chat` | POST | AI 对话（核心接口） |
| `/agent/word/enrich` | POST | AI 补全单词信息（音标、释义、例句） |
| `/agent/health` | GET | 健康检查 |
| `/agent/conversations/{id}/history` | GET | 查看对话历史 |
| `/agent/conversations/{id}` | DELETE | 删除对话 |

### /agent/chat 请求格式

```json
{
  "message": "签到",                    // 必填：用户说的话
  "user_id": 1,                         // 可选：用户 ID
  "conversation_id": "abc123",          // 可选：续聊时传，不传则新建对话
  "token": "eyJhbGciOiJIUzI1NiIs..."    // 可选：后端登录 Token
}
```

### /agent/chat 响应格式

```json
{
  "reply": "已签到！获得 10 积分",        // AI 回复
  "conversation_id": "abc123"            // 对话 ID（下次聊天传这个续聊）
}
```

---

## 🔧 配置说明（.env 文件）

```ini
# 后端 Java 的地址
API_BASE_URL=http://localhost:8080/api

# Agent 自己的服务端口
AGENT_PORT=8000

# DeepSeek API Key（必须配才能用 AI 对话）
LLM_API_KEY=sk-xxxxxxxxxxxxxxxxxxx

# 重试设置（LLM 调用失败时重试 3 次）
LLM_RETRY_MAX=3
LLM_RETRY_DELAY=1.0

# 工具执行超时（超过 15 秒没返回就报错）
TOOL_EXECUTION_TIMEOUT=15

# 日志级别：DEBUG（最详细）/ INFO / WARNING / ERROR
LOG_LEVEL=INFO
```

---

## 🔁 运行模式

### 模式一：Web 服务（主要模式）

```bash
python server.py
```

作为 HTTP 服务运行，前端或 curl 调用。

### 模式二：命令行模式

```bash
python main.py login admin 123456    # 登录
python main.py search beautiful      # 查单词
python main.py checkin               # 签到
python main.py points                # 查积分
python main.py interactive           # 交互模式
```

命令行模式**没有 AI 对话**，只是把命令翻译成后端 API 调用。

---

## 🛡️ 容错设计（挂了怎么办）

| 故障 | 反应 |
|---|---|
| DeepSeek 连不上 | 自动降级为规则回复（if-else 匹配） |
| 后端 Java 没启动 | Agent 仍可启动，只是 RAG 查不到数据 |
| Token 过期 | 报"登录已过期，请重新登录" |
| 工具执行超时 | 报"操作超时"，不影响其他功能 |
| 对话 JSON 文件损坏 | 自动新建对话 |

---

## 🚀 如何新增功能

**加一个新工具只需 3 步**：

**第 1 步**：后端 Java 加接口（如 `GET /api/store/purchase`）

**第 2 步**：`api/endpoints.py` 加一行：

```python
def purchase_book(self, book_id: int) -> dict:
    return self._c.post(f"/store/purchase/{book_id}")
```

**第 3 步**：`agent/tools.py` 加一个 Tool：

```python
Tool(
    name="purchase_book",
    description="购买指定的单词书",
    fn=api.purchase_book,
    parameters={"book_id": "要购买的词书 ID"},
)
```

重启 Agent，AI 就会自动学会用这个新工具。

---

## 📂 数据存储

所有数据存在 `data/` 目录下：

```
data/
├── conversations/         对话历史
│   ├── a1b2c3d4e5f6.json ← 每个文件是一个对话
│   └── f7e8d9c0b1a2.json
├── auth.json              登录凭证（下次启动自动恢复）
├── memory.json            简单记忆
└── agent.log              运行日志（自动轮转，10MB 滚动）
```

---

## 💡 常见问题

**Q: 一定要配 DeepSeek API Key 吗？**
A: 不配也能用，但只能用规则回复（简单 if-else），没有 AI 对话能力。

**Q: 一定要先启动后端 Java 吗？**
A: 建议先启动后端，否则 RAG 查不到数据。不过 Agent 服务本身不依赖后端也能启动。

**Q: 支持多用户吗？**
A: 支持，每个对话会话独立，通过 `conversation_id` 区分。

**Q: 聊天记录会一直保留吗？**
A: 7 天未活跃的对话会在启动时自动清理。
