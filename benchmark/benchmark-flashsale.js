/**
 * 秒杀系统专项压测 (k6)
 *
 * 重点测试: Redis 原子减库存 + MQ 异步落单
 *
 * 运行: k6 run benchmark-flashsale.js --out csv=flashsale-result.csv
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';

const BASE_URL = 'http://localhost:8080';

// 预生成 100 个测试用户（初始化时用）
const USER_POOL = new SharedArray('users', function () {
  const users = [];
  for (let i = 0; i < 1000; i++) {
    users.push({
      username: `bench_user_${i}`,
      password: 'admin123',
    });
  }
  return users;
});

// 为每个 VU 登录一次，复用 token
const tokens = {};

function getToken(userIndex) {
  const user = USER_POOL[userIndex % USER_POOL.length];
  const cacheKey = user.username;

  if (tokens[cacheKey]) return tokens[cacheKey];

  // 注册（忽略已存在错误）
  http.post(`${BASE_URL}/api/user/register`, JSON.stringify(user), {
    headers: { 'Content-Type': 'application/json' },
  });

  // 给用户充积分（秒杀需要积分）
  const loginRes = http.post(`${BASE_URL}/api/user/login`, JSON.stringify(user), {
    headers: { 'Content-Type': 'application/json' },
  });
  let token = null;
  if (loginRes.status === 200) {
    const body = loginRes.json();
    token = body.data?.token || body.token;
  }
  if (token) {
    tokens[cacheKey] = token;
  }
  return token;
}

export const options = {
  stages: [
    { duration: '10s', target: 50 },    // 快速爬升到 50
    { duration: '20s', target: 200 },   // 爬到 200
    { duration: '20s', target: 500 },   // 爬到 500
    { duration: '20s', target: 1000 },  // 峰值 1000
    { duration: '20s', target: 0 },     // 回落
  ],
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<2000'],  // 1000 并发放宽到 2s
  },
};

export default function () {
  // 每个 VU 用固定用户（避免重复登录）
  const vuId = __VU;  // k6 内置 Virtual User ID
  const token = getToken(vuId);
  if (!token) {
    sleep(1);
    return;
  }

  const headers = {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json',
  };

  // 秒杀请求
  const res = http.post(
    `${BASE_URL}/api/store/flash-sale/purchase/11`,
    null,
    { headers }
  );

  // 记录结果
  if (res.status === 200) {
    const body = res.json();
    if (body.code === 200) {
      // console.log(`✅ VU${vuId} 秒杀成功`);
    } else {
      // console.log(`❌ VU${vuId} 秒杀失败: ${body.message}`);
    }
  }

  // 秒杀场景，无间隔（纯压极限 QPS）
  // sleep(Math.random() * 0.5);
}
