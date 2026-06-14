/**
 * 专项压测：缓存读性能（Redis 命中）
 * 只测 GET /api/user/{id}，不混入 AI/秒杀等慢接口
 *
 * 运行: k6 run benchmark-cache.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = 'http://localhost:8080';

export const options = {
  stages: [
    { duration: '30s', target: 50 },    // 爬到 50 并发
    { duration: '1m',  target: 200 },   // 爬到 200
    { duration: '30s', target: 400 },   // 峰值 400
    { duration: '30s', target: 0 },
  ],
};

// setup() 只在最开始跑一次，可以发 HTTP
export function setup() {
  const res = http.post(`${BASE_URL}/api/user/login`,
    JSON.stringify({ username: 'admin', password: 'admin123' }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  const body = res.json();
  const token = body.data?.token || body.token;

  // 预热缓存：调一次用户接口，让 Redis 把数据写进去
  http.get(`${BASE_URL}/api/user/17`, {
    headers: { 'Authorization': `Bearer ${token}` },
  });

  return { token };
}

export default function (data) {
  const res = http.get(`${BASE_URL}/api/user/17`, {
    headers: { 'Authorization': `Bearer ${data.token}` },
  });
  check(res, { 'status 200': (r) => r.status === 200 });
  sleep(0.1);
}
