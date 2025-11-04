-- 固定窗口限流算法实现
-- 该脚本用于实现基于Redis的固定窗口限流逻辑

-- 参数说明:
-- KEYS[1] = 限流key，用于标识不同的限流对象(如用户ID、IP等)
-- ARGV[1] = limit，限流阈值，即窗口内允许的最大请求数
-- ARGV[2] = expire，窗口过期时间(秒)，超过该时间窗口重置

-- 获取传入参数并转换为数字类型
local key = KEYS[1]
local limit = tonumber(ARGV[1])     -- 最大请求次数限制
local expire = tonumber(ARGV[2])    -- 窗口有效期(秒)

-- 查询当前key的计数器值，如果不存在则默认为0
local current = tonumber(redis.call('get', key) or "0")

-- 判断是否超过限流阈值
if current + 1 > limit then
    -- 超过限制，拒绝本次请求，返回0表示限流
    return 0
else
    -- 未超过限制，增加计数器
    current = current + 1
    -- 更新key的值
    redis.call('set', key, current)
    -- 设置key的过期时间，实现窗口自动重置
    redis.call('expire', key, expire)
    -- 返回1表示允许通过
    return 1
end
