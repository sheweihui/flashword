/**
 * 秒杀接口压测 (k6) — v3
 *
 * 运行:
 *   k6 run benchmark-seckill.js
 */

import http from 'k6/http';
import { sleep, group } from 'k6';
import { SharedArray } from 'k6/data';
import { Rate, Trend, Counter } from 'k6/metrics';

const BASE_URL = 'http://localhost:8080';

// ========== 自定义指标 ==========
const flashDuration = new Trend('flash_duration', true);
const flashSuccess = new Rate('flash_success');
const flashAlreadyBuy = new Rate('flash_already_buy');
const flashOutOfStock = new Rate('flash_out_of_stock');
const flashRateLimited = new Rate('flash_rate_limited');
const flashOtherError = new Rate('flash_other_error');
const totalPurchased = new Counter('total_purchased');

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

// ========== Setup: 预热 Token ==========
export function setup() {
  console.log('=== 预热用户 Token ===');
  const tokens = {};
  let success = 0;

  for (let i = 0; i < 200; i++) {
    const user = USER_POOL[i];
    const res = http.post(`${BASE_URL}/api/user/login`, JSON.stringify(user), {
      headers: { 'Content-Type': 'application/json' },
      timeout: '10s',
    });

    if (res.status === 200) {
      try {
        const body = res.json();
        const token = body.data?.token || body.token;
        if (token) {
          tokens[i] = token;
          success++;
        }
      } catch (e) {}
    }
  }

  console.log(`预热完成: ${success}/200 token 成功`);
  return { tokens };
}

// ========== 配置 ==========
export const options = {
  stages: [
    { duration: '10s', target: 100 },
    { duration: '20s', target: 200 },
    { duration: '30s', target: 200 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.05'],
  },
  noConnectionReuse: false,
};

// ========== 主测试 ==========
export default function (data) {
  const vuId = __VU - 1;
  const token = data.tokens[vuId];

  if (!token) {
    flashOtherError.add(1);
    sleep(1);
    return;
  }

  const headers = {
    'Authorization': `Bearer ${token}`,
    'Content-Type': 'application/json',
  };

  group('秒杀抢购', function () {
    const res = http.post(
      `${BASE_URL}/api/store/flash-sale/purchase/12`,
      null,
      { headers, timeout: '30s' }
    );

    flashDuration.add(res.timings.duration);

    if (res.status !== 200) {
      flashOtherError.add(1);
      return;
    }

    let body;
    try {
      body = res.json();
    } catch (e) {
      flashOtherError.add(1);
      return;
    }

    // 判断业务状态码
    if (body.code === 200) {
      // 真正抢购成功
      flashSuccess.add(1);
      totalPurchased.add(1);
    } else {
      const msg = (body.message || '').toLowerCase();
      if (msg.includes('请勿重复') || msg.includes('已经抢购')) {
        flashAlreadyBuy.add(1);
      } else if (msg.includes('库存') || msg.includes('抢光')) {
        flashOutOfStock.add(1);
      } else if (msg.includes('限流') || msg.includes('频繁') || msg.includes('火爆')) {
        flashRateLimited.add(1);
      } else if (msg.includes('积分不足')) {
        flashOtherError.add(1);
      } else {
        // 其他 SQL 错误等
        flashOtherError.add(1);
      }
    }
  });

  // 每个用户每次迭代间隔 0.3~0.8 秒
  sleep(0.3 + Math.random() * 0.5);
}
