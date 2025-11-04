-- 滑动窗口限流算法实现
-- 该脚本用于实现基于Redis的滑动窗口限流逻辑，相比固定窗口算法更加精确

-- 参数说明:
-- KEYS[1] = 限流key，用于标识不同的限流对象(如用户ID、IP等)
-- ARGV[1] = limit，限流阈值，即窗口内允许的最大请求数
-- ARGV[2] = window size，窗口大小(毫秒)，定义时间窗口的长度
-- ARGV[3] = current timestamp，当前时间戳(毫秒)

-- 获取传入参数并转换为数字类型
local key = KEYS[1]
local limit = tonumber(ARGV[1])     -- 最大请求次数限制
local window = tonumber(ARGV[2])    -- 窗口大小(毫秒)
local now = tonumber(ARGV[3])       -- 当前时间戳(毫秒)

-- 清除窗口外的过期请求记录
-- 删除score小于(now - window)的元素，即保留当前时间窗口内的请求记录
redis.call('zremrangebyscore', key, 0, now - window)

-- 统计当前窗口内的请求数量
local count = redis.call('zcard', key)

-- 判断是否超过限流阈值
if count < limit then
    -- 未超过限制，允许本次请求
    -- 将当前时间戳作为score和member添加到有序集合中
    redis.call('zadd', key, now, now)
    -- 设置过期时间，确保key在窗口期过后自动清理
    -- 过期时间 = 窗口大小(毫秒) / 1000 = 秒数，向上取整
    redis.call('expire', key, math.ceil(window / 1000))
    -- 返回1表示允许通过
    return 1
else
    -- 超过限制，拒绝本次请求，返回0表示限流
    return 0
end
