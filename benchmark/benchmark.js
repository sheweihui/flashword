/**
 * 单词学习App 压测脚本 (k6)
 *
 * 使用方式:
 *   1. 安装 k6: winget install k6
 *   2. 确保后端已启动 (http://localhost:8080)
 *   3. 运行: k6 run benchmark.js
 *
 * 输出指标:
 *   - http_req_duration: 请求延迟 (avg, p50, p95, p99)
 *   - http_req_failed: 失败率
 *   - iterations: 总请求数 (QPS = iterations / 运行时间)
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// ==================== 自定义指标 ====================
const cacheHitTrend = new Trend('cache_hit_duration_ms');
const cacheMissTrend = new Trend('cache_miss_duration_ms');
const dbQueryTrend = new Trend('db_query_duration_ms');
const loginTrend = new Trend('login_duration_ms');
const flashSaleTrend = new Trend('flash_sale_duration_ms');

// ==================== 配置 ====================
const BASE_URL = 'http://localhost:8080';
const DEFAULT_USER = { username: 'benchmark_user', password: '123456' };

// 注册测试用户（首次运行用）
function ensureTestUser() {
  const payload = JSON.stringify({
    username: DEFAULT_USER.username,
    password: DEFAULT_USER.password,
    nickname: '压测用户',
  });
  http.post(`${BASE_URL}/api/user/register`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });
  // 注册失败也无所谓（可能已存在）
}

// 登录并获取 token
function login() {
  const payload = JSON.stringify({
    username: DEFAULT_USER.username,
    password: DEFAULT_USER.password,
  });
  const res = http.post(`${BASE_URL}/api/user/login`, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'login' },
  });

  check(res, { 'login status 200': (r) => r.status === 200 });
  loginTrend.add(res.timings.duration);

  // 解析 token（兼容两种返回格式）
  try {
    const body = res.json();
    if (body.data && body.data.token) {
      return { token: body.data.token, userId: body.data.userId };
    }
    return { token: body.token, userId: body.userId };
  } catch {
    return null;
  }
}

// ==================== 加载配置 ====================
export const options = {
  stages: [
    // 阶段1：缓慢爬升，观察初始表现
    { duration: '30s', target: 10 },   // 10 并发
    // 阶段2：中等压力
    { duration: '1m', target: 30 },    // 30 并发
    // 阶段3：高压力
    { duration: '1m', target: 50 },    // 50 并发
    // 阶段4：峰值
    { duration: '30s', target: 100 },  // 100 并发
    // 阶段5：回落实测
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    // 硬性门限：失败率不超过 5%
    http_req_failed: ['rate<0.05'],
    // 95% 请求不超过 3s
    http_req_duration: ['p(95)<3000'],
  },
};

// ==================== 主脚本 ====================
export default function () {
  // ---- 前置：登录 ----
  const session = login();
  if (!session || !session.token) {
    console.error('登录失败，跳过本轮');
    sleep(1);
    return;
  }

  const authHeaders = {
    'Authorization': `Bearer ${session.token}`,
    'Content-Type': 'application/json',
  };

  const userId = session.userId || 1;

  // ---- 场景1：用户信息（缓存命中） ----
  // 首次登录后，consumeUserLogin 和 UserMessageConsumer 会将
  // 用户信息、单词本、积分写入 Redis 缓存，后续查 GET /api/user/{id}
  // 期望命中缓存 → 低延迟
  group('场景1: 缓存命中读', function () {
    const res = http.get(`${BASE_URL}/api/user/${userId}`, {
      headers: authHeaders,
      tags: { name: 'cache_hit' },
    });
    check(res, { 'user info ok': (r) => r.status === 200 });
    cacheHitTrend.add(res.timings.duration);
  });

  // ---- 场景2：单词搜索（数据库查询） ----
  group('场景2: 数据库查询', function () {
    const keywords = ['hello', 'world', 'book', 'study', 'english', 'test', 'apple', 'dog'];
    const keyword = keywords[Math.floor(Math.random() * keywords.length)];
    const res = http.get(`${BASE_URL}/api/word/search?keyword=${keyword}`, {
      headers: authHeaders,
      tags: { name: 'db_query' },
    });
    check(res, { 'word search ok': (r) => r.status === 200 });
    dbQueryTrend.add(res.timings.duration);
  });

  // ---- 场景3：商店列表（数据库查询） ----
  group('场景2b: 分页查询', function () {
    const page = Math.floor(Math.random() * 5) + 1;
    const res = http.get(`${BASE_URL}/api/store/books?page=${page}&size=10`, {
      headers: authHeaders,
      tags: { name: 'paged_query' },
    });
    check(res, { 'store books ok': (r) => r.status === 200 });
    dbQueryTrend.add(res.timings.duration);
  });

  // ---- 场景4：秒杀（Redis 原子操作 + MQ，你的亮点功能） ----
  group('场景3: 秒杀并发', function () {
    const res = http.post(
      `${BASE_URL}/api/store/flash-sale/purchase/1`,
      null,
      { headers: authHeaders, tags: { name: 'flash_sale' } }
    );
    // 秒杀可能返回成功，也可能返回"已售罄"（都是正常业务逻辑）
    check(res, {
      'flash sale responded': (r) => r.status === 200,
    });
    flashSaleTrend.add(res.timings.duration);
  });

  // ---- 场景5：AI 填充（走 Python Agent → DeepSeek） ----
  // 这个接口受 LLM 速度限制，QPS 很低，单独标记
  group('场景4: AI单词填充(LLM)', function () {
    const words = ['hello', 'beautiful', 'opportunity', 'knowledge'];
    const word = words[Math.floor(Math.random() * words.length)];
    const res = http.post(
      `${BASE_URL}/api/word/ai-fill`,
      JSON.stringify({ wordText: word }),
      { headers: authHeaders, tags: { name: 'ai_fill' } }
    );
    check(res, { 'ai fill ok': (r) => r.status === 200 || r.status === 503 });
  });

  // 思考时间：模拟真实用户操作间隔
  sleep(Math.random() * 2 + 0.5);
}

// ---- 初始化（仅运行一次） ----
export function setup() {
  console.log('🔧 初始化：确保测试用户存在...');
  ensureTestUser();
  console.log('✅ 初始化完成，开始压测');
}
