-- 令牌桶限流 Lua 脚本
-- KEYS[1] = 令牌桶 key
-- ARGV[1] = 桶容量（最大突发）
-- ARGV[2] = 每秒填充速率
-- ARGV[3] = 当前时间戳（秒）
-- 返回值: 1=通过, 0=被限流

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refillRate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

-- bucket 数据结构: { tokens=剩余令牌, last_refill=上次填充时间 }
local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens = tonumber(bucket[1])
local lastRefill = tonumber(bucket[2])

if tokens == nil then
    -- 桶不存在，初始化
    tokens = capacity
    lastRefill = now
end

-- 计算需要填充的令牌数
local elapsed = now - lastRefill
if elapsed > 0 then
    local refill = elapsed * refillRate
    tokens = math.min(capacity, tokens + refill)
    lastRefill = now
end

-- 尝试取令牌
if tokens >= 1 then
    tokens = tokens - 1
    redis.call('HMSET', key, 'tokens', tokens, 'last_refill', lastRefill)
    redis.call('EXPIRE', key, math.ceil(capacity / refillRate) + 5)
    return 1
else
    redis.call('HMSET', key, 'tokens', tokens, 'last_refill', lastRefill)
    return 0
end
