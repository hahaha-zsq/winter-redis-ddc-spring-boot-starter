package com.zsq.winter.redis.ddc.enums;

public enum LimitAlgorithm {
    FIXED_WINDOW,   // 固定窗口
    SLIDING_WINDOW, // 滑动窗口
    TOKEN_BUCKET    // 令牌桶
}