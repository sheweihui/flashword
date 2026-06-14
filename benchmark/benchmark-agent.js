/**
 * Agent 接口压测 — 轻量版
 *
 * 注意: AI 对话/单词补全调 DeepSeek API（有费用+限流）
 *       健康检查不调用 DeepSeek，可加大压力
 *
 * 运行:
 *   k6 run benchmark-agent.js
 */

import http from 'k6/http';
import { sleep, group, check } from 'k6';
import { SharedArray } from 'k6/data';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = 'http://localhost:8000';

// ========== 指标 ==========
const healthDuration = new Trend('health_duration', true);
const chatDuration = new Trend('chat_duration', true);
const wordDuration = new Trend('word_duration', true);

// ========== 测试数据 ==========
const MESSAGES = new SharedArray('messages', function () {
  return ['hello', '背单词技巧', 'apple什么意思', '英语怎么学', '推荐学习资源'];
});

const WORDS = new SharedArray('words', function () {
  return ['apple', 'beautiful', 'knowledge', 'challenge', 'opportunity'];
});

// ========== 配置 ==========
export const options = {
  stages: [
    { duration: '5s', target: 20 },    // 健康检查可承受较高并发
    { duration: '10s', target: 5 },     // AI 对话用低并发（DeepSeek 限流）
    { duration: '10s', target: 5 },
    { duration: '5s', target: 0 },
  ],
};

// ========== 主测试 ==========
export default function () {
  const iter = __ITER;

  // A: 健康检查（高并发）
  group('健康检查', function () {
    const res = http.get(`${BASE_URL}/agent/health`, { timeout: '5s' });
    healthDuration.add(res.timings.duration);
  });

  // B: AI 对话（低并发，调 DeepSeek）
  group('AI对话', function () {
    // 只有前 5 个 VU 发对话请求（减少 DeepSeek 调用）
    if (__VU <= 5) {
      const msg = MESSAGES[iter % MESSAGES.length];
      const res = http.post(`${BASE_URL}/agent/chat`, JSON.stringify({
        message: msg,
        user_id: iter % 100,
      }), {
        headers: { 'Content-Type': 'application/json' },
        timeout: '30s',
      });
      chatDuration.add(res.timings.duration);
      if (res.status === 200) {
        try {
          const body = res.json();
          check(body, { '有回复': (b) => b.reply && b.reply.length > 5 });
        } catch (e) {}
      }
    }
  });

  // C: 单词补全（低并发，调 DeepSeek）
  group('单词补全', function () {
    if (__VU <= 5) {
      const word = WORDS[iter % WORDS.length];
      const res = http.post(`${BASE_URL}/agent/word/enrich`, JSON.stringify({
        word_text: word,
        user_id: iter % 100,
      }), {
        headers: { 'Content-Type': 'application/json' },
        timeout: '15s',
      });
      wordDuration.add(res.timings.duration);
    }
  });

  // 间隔 2-3s 避免 DeepSeek 限流
  sleep(2 + Math.random());
}
