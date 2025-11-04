package com.zsq.winter.redis.ddc.expection;

public class RateLimitException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public RateLimitException(String msg) { super(msg); }

    public RateLimitException(String msg, Throwable cause) { super(msg, cause); }
}