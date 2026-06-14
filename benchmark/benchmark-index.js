/**
 * 索引优化前后压测 (k6)
 *
 * 测试场景:
 *   A — isPurchased() 查询（selectByUserAndProduct）
 *   B — 商店列表查询（queryStoreBooks + selectByFilter）
 *
 * 运行:
 *   k6 run benchmark-index.js --out csv=index-result.csv
 *
 * 对比流程:
 *   1. 确保后端已启动, MySQL 已插入 50000+ 购买记录
 *   2. 先跑一次: 不加索引的 BEFORE 压测
 *   3. 执行 benchmark-index.sql 添加索引
 *   4. 再跑一次: 加索引后的 AFTER 压测
 */

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { SharedArray } from 'k6/data';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = 'http://localhost:8080';

// ========== 场景 A 指标 ==========
const sceneA_duration = new Trend('sceneA_duration', true);
const sceneA_success = new Rate('sceneA_success');
const sceneA_alreadyPurchased = new Rate('sceneA_alreadyPurchased');

// ========== 场景 B 指标 ==========
const sceneB_duration = new Trend('sceneB_duration', true);
const sceneB_success = new Rate('sceneB_success');

// ========== 用户池 ==========
const USER_POOL = new SharedArray('users', function () {
  const users = [];
  for (let i = 0; i < 200; i++) {
    users.push({
      username: `bench_user_${i}`,
      password: 'admin123',
    });
  }
  return users;
});

// token 缓存
const tokens = {};

function getToken(userIndex) {
  const user = USER_POOL[userIndex % USER_POOL.length];
  const cacheKey = user.username;
  if (tokens[cacheKey]) return tokens[cacheKey];

  // 注册（忽略已存在错误）
  http.post(`${BASE_URL}/api/user/register`, JSON.stringify(user), {
    headers: { 'Content-Type': 'application/json' },
  });

  // 登录
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
    { duration: '5s', target: 50 },   // 快速爬升到 50
    { duration: '15s', target: 100 },  // 到 100
    { duration: '20s', target: 100 },  // 保持 100 并发
    { duration: '10s', target: 0 },    // 回落
  ],
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<3000'],
  },
};

export default function () {
  const vuId = __VU;
  const token = getToken(vuId);
  if (!token) {
    sleep(1);
    return;
  }

  const headers = {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json',
  };

  // ========== 场景 A: isPurchased 查询 ==========
  group('场景A: isPurchased 购买检查', function () {
    // 所有 bench_user 都已购买商品 14（由 benchmark-index.sql 插入）
    // 因此调用 purchase 会立刻被 isPurchased 拦截，返回 "已购买"
    const res = http.post(
      `${BASE_URL}/api/store/books/14/purchase`,
      null,
      { headers }
    );

    sceneA_duration.add(res.timings.duration);
    sceneA_success.add(res.status === 200);

    if (res.status === 200) {
      const body = res.json();
      // 购买成功说明是 200 + code=200，但预期是失败（已购买）
      if (body.code === 200) {
        // 实际上不应该购买成功（已有记录）
        // console.log(`⚠️ VU${vuId} 购买成功（预期外）`);
      }
    } else if (res.status === 500) {
      const body = res.json();
      if (body && body.message && body.message.includes('已购买')) {
        sceneA_alreadyPurchased.add(1);
        // console.log(`✅ VU${vuId} 正确拦截: 已购买`);
      }
    }
  });

  // ========== 场景 B: 商店列表 ==========
  group('场景B: 商店列表查询', function () {
    const res = http.get(
      `${BASE_URL}/api/store/books?page=1&size=20&category=cet4`,
      { headers }
    );

    sceneB_duration.add(res.timings.duration);
    sceneB_success.add(res.status === 200);
  });

  // 每个 VU 循环间隔 1-2 秒
  sleep(1 + Math.random());
}
