package com.zsq.winter.redis.ddc.service;

import com.zsq.winter.redis.ddc.enums.LimitAlgorithm;
import com.zsq.winter.redis.ddc.util.LuaScriptLoader;
import org.redisson.api.RScript;
import org.redisson.client.codec.LongCodec;

import java.util.Collections;

/**
 * 限流服务实现类，基于Redisson提供分布式限流功能
 * 支持固定窗口、滑动窗口和令牌桶三种限流算法
 */
public class RateLimiterService {

    /**
     * Redisson模板工具类，用于执行Redis相关操作
     */
    private final WinterRedissionTemplate winterRedissionTemplate;

    /**
     * 固定窗口限流Lua脚本内容
     */
    private final String fixedLua;

    /**
     * 滑动窗口限流Lua脚本内容
     */
    private final String slidingLua;

    /**
     * 令牌桶限流Lua脚本内容
     */
    private final String tokenLua;

    /**
     * 构造函数，初始化各种限流算法的Lua脚本
     *
     * @param winterRedissionTemplate Redisson操作模板
     */
    public RateLimiterService(WinterRedissionTemplate winterRedissionTemplate) {
        this.winterRedissionTemplate = winterRedissionTemplate;
        // 加载固定窗口限流Lua脚本
        this.fixedLua = LuaScriptLoader.load("lua/fixed_window.lua");
        // 加载滑动窗口限流Lua脚本
        this.slidingLua = LuaScriptLoader.load("lua/sliding_window.lua");
        // 加载令牌桶限流Lua脚本
        this.tokenLua = LuaScriptLoader.load("lua/token_bucket.lua");
    }

    /**
     * 尝试获取令牌，执行限流检查
     *
     * @param key              限流键名（业务维度标识）
     * @param algorithm        限流算法类型
     * @param permitsPerSecond 每秒允许的请求数
     * @param windowSizeSec    时间窗口大小（秒），仅对窗口算法有效
     * @param capacity         桶容量，仅对令牌桶算法有效
     * @return true 如果允许通过，false 如果被限流
     */
    public boolean tryAcquire(String key, LimitAlgorithm algorithm, double permitsPerSecond, long windowSizeSec, long capacity) {
        // 获取Redis脚本执行器，使用LongCodec编码器
        RScript script = winterRedissionTemplate.getScript(LongCodec.INSTANCE);
        // 获取当前时间戳（毫秒）
        long now = System.currentTimeMillis();

        // 根据不同的限流算法类型执行相应的限流逻辑
        switch (algorithm) {
            case FIXED_WINDOW: {
                // 固定窗口算法
                // ARGV参数: limit(限制数量), expire(过期时间秒)
                Long res = script.eval(RScript.Mode.READ_WRITE, fixedLua, RScript.ReturnType.INTEGER,
                        Collections.singletonList("ratelimit:fw:" + key),  // Redis key前缀为ratelimit:fw:
                        toLong(permitsPerSecond), windowSizeSec);          // 传递限流参数
                // 返回1表示允许通过，其他值表示被限流
                return res != null && res == 1L;
            }
            case SLIDING_WINDOW: {
                // 滑动窗口算法
                // ARGV参数: limit(限制数量), window_ms(窗口大小毫秒), now_ms(当前时间毫秒)
                Long res = script.eval(RScript.Mode.READ_WRITE, slidingLua, RScript.ReturnType.INTEGER,
                        Collections.singletonList("ratelimit:sw:" + key),  // Redis key前缀为ratelimit:sw:
                        toLong(permitsPerSecond), windowSizeSec * 1000, now); // 传递限流参数
                // 返回1表示允许通过，其他值表示被限流
                return res != null && res == 1L;
            }
            case TOKEN_BUCKET:
            default: {
                // 令牌桶算法（默认算法）
                // 计算桶容量：如果未指定容量则使用每秒允许数的2倍，最小为1
                long cap = capacity > 0 ? capacity : Math.max(1, (long) (permitsPerSecond * 2));
                // ARGV参数: rate(产生令牌速率), capacity(桶容量), now(当前时间), requested(请求令牌数)
                Long res = script.eval(RScript.Mode.READ_WRITE, tokenLua, RScript.ReturnType.INTEGER,
                        Collections.singletonList("ratelimit:tb:" + key),  // Redis key前缀为ratelimit:tb:
                        toLong(permitsPerSecond), cap, now, 1);            // 传递限流参数
                // 返回1表示允许通过，其他值表示被限流
                return res != null && res == 1L;
            }
        }
    }

    /**
     * 将double类型数值向上取整转换为long类型
     *
     * @param d double数值
     * @return 向上取整后的long值
     */
    private static long toLong(double d) {
        return (long) Math.ceil(d);
    }
}
