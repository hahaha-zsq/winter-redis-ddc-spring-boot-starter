package com.zsq.winter.redis.ddc.entity;

public class Constants {

    public final static String DYNAMIC_CONFIG_CENTER_REDIS_TOPIC = "DYNAMIC_CONFIG_CENTER_REDIS_TOPIC_";

    public final static String SYMBOL_COLON = ":";

    public static String getTopic(String application) {
        return DYNAMIC_CONFIG_CENTER_REDIS_TOPIC + application;
    }

}
