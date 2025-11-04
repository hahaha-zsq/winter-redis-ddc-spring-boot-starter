package com.zsq.winter.redis.ddc.annotation;

import com.zsq.winter.redis.ddc.enums.LimitAlgorithm;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {
    /**
     * 支持 SpEL 表达式，例如 "#userId + ':create'"
     */
    String key();

    /**
     * 每秒允许的请求数 (语义依据算法)
     */
    double permitsPerSecond() default 1.0;

    /**
     * 窗口大小（秒），对固定/滑动窗口生效
     */
    long windowSize() default 1;

    /**
     * 算法类型
     */
    LimitAlgorithm algorithm() default LimitAlgorithm.TOKEN_BUCKET;

    /**
     * 桶容量（仅对令牌桶生效）
     */
    long capacity() default -1L;
}