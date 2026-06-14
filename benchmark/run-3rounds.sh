#!/bin/bash
# 秒杀压测 — 自动三轮，每轮前重置数据
# 在 benchmark 目录下运行

cd "$(dirname "$0")" || exit 1
ACTIVITY_ID=13
STOCK=100
K6_SCRIPT="benchmark-seckill-1000.js"

echo "========================================"
echo "  秒杀压测：1000用户 / ${STOCK}单 / 三轮"
echo "  配置: Tomcat500 / Hikari100 / MQ prefetch=5"
echo "========================================"

reset_data() {
  local round=$1
  echo ""
  echo "--- [Round ${round}] 重置数据 ---"

  # 重置 Redis 库存
  docker exec vocabulary-redis redis-cli SET "wf:stock:flash:${ACTIVITY_ID}" ${STOCK} > /dev/null
  echo "  Redis stock → ${STOCK}"

  # 删除 idempotent keys
  docker exec vocabulary-redis redis-cli EVAL "return redis.call('DEL', unpack(redis.call('KEYS', ARGV[1])))" 0 "wf:idempotent:order:*:${ACTIVITY_ID}" > /dev/null 2>&1
  echo "  Idempotent keys cleared"

  # 重置 DB 库存 + 清理订单
  docker exec vocabulary-mysql mysql -uroot -p123456 app -e \
    "UPDATE seckill_activity SET stock = ${STOCK} WHERE id = ${ACTIVITY_ID}; DELETE FROM seckill_order WHERE activity_id = ${ACTIVITY_ID};" 2>/dev/null
  echo "  DB stock → ${STOCK}, orders cleared"

  # 等待 MQ 消化
  echo "  Waiting 10s for MQ drain..."
  sleep 10

  # 二次清理（MQ 可能产生了新订单）
  docker exec vocabulary-mysql mysql -uroot -p123456 app -e \
    "DELETE FROM seckill_order WHERE activity_id = ${ACTIVITY_ID};" 2>/dev/null
  docker exec vocabulary-redis redis-cli EVAL "return redis.call('DEL', unpack(redis.call('KEYS', ARGV[1])))" 0 "wf:idempotent:order:*:${ACTIVITY_ID}" > /dev/null 2>&1
  docker exec vocabulary-redis redis-cli SET "wf:stock:flash:${ACTIVITY_ID}" ${STOCK} > /dev/null
  echo "  Final cleanup done"
}

for ROUND in 1 2 3; do
  reset_data $ROUND

  echo ""
  echo "=== Round ${ROUND} starting ==="

  # Run k6 with json output
  k6 run --out json="k6-raw-round${ROUND}.json" "${K6_SCRIPT}" 2>&1 | tee "round${ROUND}-output.txt"

  echo "=== Round ${ROUND} done ==="

  if [ $ROUND -lt 3 ]; then
    echo "Waiting 15s for MQ..."
    sleep 15
  fi
done

echo ""
echo "All 3 rounds complete!"
echo "Output: round{1,2,3}-output.txt"
