# FlashWord — 背单词 App 全栈项目

> 一个完整的背单词小程序全栈项目，包含 **Spring Boot 后端** + **微信小程序前端** + **AI Agent 中间层**，覆盖单词学习、AI 对话、商店积分、秒杀抢购等完整业务链路。

---

## 📋 目录

- [项目架构](#项目架构)
- [模块概览](#模块概览)
- [技术栈](#技术栈)
- [快速启动](#快速启动)
- [项目亮点](#项目亮点)

---

## 项目架构

```
用户 (微信小程序)
    │
    ▼
┌──────────────────────────────────────────────────┐
│  frontend/ (微信小程序)                            │
│  首页 / 背单词 / AI 对话 / 商店 / 秒杀 / 个人中心    │
└──────────────────────┬───────────────────────────┘
                       │ HTTP
                       ▼
┌──────────────────────────────────────────────────┐
│  backend/ (Spring Boot :8080)                     │
│  单词管理 / 词本管理 / 签到 / 积分 / 商店 / 秒杀    │
│  JWT 鉴权 / Redis 缓存 / RabbitMQ / MyBatis      │
└──────────────────────┬───────────────────────────┘
                       │ HTTP
                       ▼
┌──────────────────────────────────────────────────┐
│  python-agent/ (AI Agent :8000)                   │
│  FastAPI + DeepSeek LLM                          │
│  Hybrid Search RAG + MCP 协议 + 熔断降级           │
│  → 自然语言对话 → Function Calling → 调用后端 API  │
└──────────────────────────────────────────────────┘
```

### 交互流程

```
用户说 "beautiful 是什么意思，帮我签到"
    │
    ▼
AI Agent 接收 → QueryProcessor 意图分类 [查词, 签到]
    │
    ├─ RAG 并发检索 (→ 后端查词典 + 查词本 + 查积分)
    ├─ 拼装上下文 → DeepSeek LLM
    ├─ Function Calling → 调后端签到 API
    └─ 返回 "beautiful 是美丽的，已签到 +10 积分"
```

---

## 模块概览

### backend/ — Spring Boot 后端

基于 **Spring Boot 3.2 + MyBatis + Redis + RabbitMQ** 构建的背单词 App 服务端。

| 功能 | 说明 |
|------|------|
| 用户系统 | 注册/登录、JWT 鉴权、Token 拦截器 |
| 单词管理 | 单词 CRUD、公共词库、AI 补全单词信息 |
| 词本管理 | 购买词书、自建词本、单词收录 |
| 签到积分 | 每日签到、积分流水、连续签到奖励 |
| 商店系统 | 词书商品、秒杀活动（RabbitMQ + Redis 限流）|
| 基础设施 | Redis 缓存、AOP 日志、慢查询记录、全局异常处理 |

### frontend/ — 微信小程序

基于微信小程序原生开发的背单词客户端，包含 8 个页面。

| 页面 | 功能 |
|------|------|
| 登录 | 手机号/密码登录 |
| 首页 | 学习统计、每日推荐、快捷入口 |
| 背单词 | 单词卡片、释义/例句/发音、词本切换 |
| AI 对话 | 自然语言查词/签到/积分查询、知识库问答 |
| 商店 | 词书购买、秒杀活动 |
| 个人中心 | 积分余额、词本管理、学习记录 |

### python-agent/ — AI Agent 中间层

基于 **FastAPI + DeepSeek** 的 AI Agent，作为前端和后端之间的智能中间层。

| 能力 | 说明 |
|------|------|
| 自然语言对话 | 用户用大白话查词、签到、看积分 |
| Hybrid Search 混合检索 | BM25 + Dense Embedding + RRF 融合 |
| MCP 协议工具调用 | 8 个业务工具，支持多轮函数调用 |
| 多格式文档解析 | PDF/PPTX/DOCX/TXT/MD 自动入库语义搜索 |
| 可插拔架构 | Embedding/Splitter/Loader 全部工厂模式 |
| 对话上下文管理 | 自动摘要压缩、Token 预算裁剪、过期清理 |
| 熔断降级 | 四组件独立熔断器，自动降级探活恢复 |
| 全链路 Trace | RAG 各阶段耗时记录，可追溯每次请求链路 |

---

## 技术栈

| 层级 | 技术 |
|------|------|
| **前端** | 微信小程序、ES6 |
| **后端** | Spring Boot 3.2、MyBatis、MySQL、Redis、RabbitMQ、JWT |
| **AI Agent** | Python、FastAPI、DeepSeek、ChromaDB、MCP |
| **检索** | BM25、Dense Embedding、RRF、jieba |
| **工具** | Docker、Maven、k6（压测）|

---

## 快速启动

### 1. 启动后端

```bash
cd backend
# 确保 MySQL + Redis + RabbitMQ 已启动
mvn spring-boot:run
# 服务运行在 http://localhost:8080
```

### 2. 启动 AI Agent

```bash
cd python-agent
python -m venv .venv
source .venv/bin/activate  # Windows: .venv\Scripts\activate
pip install -r requirements.txt
cp .env.example .env        # 编辑填入 DeepSeek API Key
python server.py            # 服务运行在 http://localhost:8000
```

### 3. 运行前端

使用微信开发者工具打开 `frontend/` 目录，配置 `app.js` 中的后端地址即可。

---

## 项目亮点

| # | 亮点 | 说明 |
|---|------|------|
| 1 | **Hybrid Search** | BM25 + Dense 双路召回 + RRF 融合，jieba 中英文分词 |
| 2 | **可插拔 RAG Pipeline** | Embedding/Splitter/Loader 全部工厂模式，配置驱动切换 |
| 3 | **多格式文档解析** | LoaderFactory 统一处理 PDF/PPTX/DOCX/TXT/MD |
| 4 | **对话上下文管理** | 自动摘要压缩 + Token 裁剪 + 并发 4 路 RAG 检索 |
| 5 | **熔断降级** | 四组件独立熔断器，状态机自动降级与探活恢复 |
| 6 | **全链路 Trace** | RAG 各阶段耗时记录至 JSON Lines，全程可追溯 |
| 7 | **秒杀系统** | RabbitMQ 削峰 + Redis 限流 + 库存原子扣减 |
| 8 | **AOP 工程化** | 方法耗时统计、日志切面、慢查询记录 |
