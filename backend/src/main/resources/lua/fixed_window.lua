-- 固定窗口限流 Lua 脚本
-- KEYS[1] = 限流 key（如 wf:rate:user:seckill:{userId}:{activityId}）
-- ARGV[1] = 窗口内最大请求数
-- ARGV[2] = 窗口大小（秒）
-- 返回值: 1=通过, 0=被限流

local key = KEYS[1]
local maxRequests = tonumber(ARGV[1])
local windowSec = tonumber(ARGV[2])

local current = redis.call('INCR', key)

if current == 1 then
    -- 第一次请求，设置过期时间
    redis.call('EXPIRE', key, windowSec)
end

if current <= maxRequests then
    return 1  -- 通过
else
    return 0  -- 被限流
end
