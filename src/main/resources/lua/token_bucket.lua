-- 令牌桶限流算法实现
-- 该脚本用于实现基于Redis的令牌桶限流逻辑，支持突发流量处理

-- 参数说明:
-- KEYS[1] = 限流key，用于标识不同的限流对象(如用户ID、IP等)
-- ARGV[1] = rate，令牌生成速率(个/秒)，每秒向桶中添加的令牌数
-- ARGV[2] = capacity，桶容量，桶中最多能存放的令牌数
-- ARGV[3] = now，当前时间戳(毫秒)
-- ARGV[4] = requested，本次请求需要消耗的令牌数量

-- 获取传入参数并转换为数字类型
local key = KEYS[1]
local rate = tonumber(ARGV[1])          -- 令牌生成速率(个/秒)
local capacity = tonumber(ARGV[2])      -- 桶的最大容量
local now = tonumber(ARGV[3])           -- 当前时间戳(毫秒)
local requested = tonumber(ARGV[4])     -- 本次请求需要的令牌数

-- 从Redis Hash中获取桶的当前状态
-- last_tokens: 桶中剩余的令牌数，如果key不存在则初始化为桶容量
local last_tokens = tonumber(redis.call('hget', key, 'tokens')) or capacity
-- last_refreshed: 上次更新时间戳，如果key不存在则初始化为当前时间
local last_refreshed = tonumber(redis.call('hget', key, 'timestamp')) or now

-- 计算距离上次更新经过的时间(秒)
local delta = math.max(0, now - last_refreshed) / 1000.0

-- 根据时间差计算当前桶中应该有的令牌数
-- 令牌数 = 原有令牌数 + 时间差(秒) * 令牌生成速率
-- 使用math.min确保令牌数不超过桶容量
local filled_tokens = math.min(capacity, last_tokens + (delta * rate))

-- 判断桶中令牌是否足够满足本次请求
local allowed = filled_tokens >= requested

-- 计算消耗令牌后桶中剩余的令牌数
local new_tokens = filled_tokens
if allowed then
    -- 如果允许通过，则扣除相应令牌数
    new_tokens = filled_tokens - requested
end

-- 更新桶状态到Redis Hash中
redis.call('hset', key, 'tokens', new_tokens)    -- 更新剩余令牌数
redis.call('hset', key, 'timestamp', now)        -- 更新时间戳

-- 返回结果：允许通过返回1，拒绝返回0
return allowed and 1 or 0
