/**
 * 秒杀接口压测 — 1000用户 / 100单 / 三轮
 *
 * 运行:
 *   k6 run benchmark-seckill-1000.js
 *
 * 每轮前通过 API 重置 Redis 库存 + 清理订单
 */

import http from 'k6/http';
import { sleep, group, check } from 'k6';
import { SharedArray } from 'k6/data';
import { Rate, Trend, Counter } from 'k6/metrics';

const BASE_URL = 'http://localhost:8080';

// ========== 配置（单轮，三轮手动跑） ==========
const ACTIVITY_ID = 13;
const TOTAL_STOCK = 100;
const VU_COUNT = 1000;

// ========== 指标 ==========
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
  for (let i = 0; i < VU_COUNT; i++) {
    users.push({
      username: `bench_user_${i}`,
      password: 'admin123',
    });
  }
  return users;
});

// ========== Setup: 预热 Token（分批并发） ==========
export function setup() {
  console.log(`=== 预热 ${VU_COUNT} 个 Token ===`);
  const tokens = {};
  let success = 0;
  const BATCH_SIZE = 20;

  for (let start = 0; start < VU_COUNT; start += BATCH_SIZE) {
    const batch = [];
    const end = Math.min(start + BATCH_SIZE, VU_COUNT);
    for (let i = start; i < end; i++) {
      const user = USER_POOL[i];
      batch.push({
        method: 'POST',
        url: `${BASE_URL}/api/user/login`,
        body: JSON.stringify(user),
        params: {
          headers: { 'Content-Type': 'application/json' },
          timeout: '10s',
        },
      });
    }

    const responses = http.batch(batch);
    for (let j = 0; j < responses.length; j++) {
      const idx = start + j;
      const res = responses[j];
      if (res.status === 200) {
        try {
          const body = res.json();
          const token = body.data?.token || body.token;
          if (token) {
            tokens[idx] = token;
            success++;
          }
        } catch (e) {}
      }
    }
  }

  console.log(`预热完成: ${success}/${VU_COUNT} token 成功`);
  return { tokens };
}

// ========== 配置 ==========
export const options = {
  setupTimeout: '300s',
  stages: [
    { duration: '10s', target: 500 },
    { duration: '20s', target: 1000 },
    { duration: '20s', target: 1000 },
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
      `${BASE_URL}/api/store/flash-sale/purchase/${ACTIVITY_ID}`,
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
        flashOtherError.add(1);
      }
    }
  });

  // 每个用户较长间隔，减少限流触发
  sleep(1 + Math.random() * 0.5);
}
