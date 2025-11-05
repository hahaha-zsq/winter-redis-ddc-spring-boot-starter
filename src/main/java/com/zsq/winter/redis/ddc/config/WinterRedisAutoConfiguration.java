package com.zsq.winter.redis.ddc.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zsq.winter.redis.ddc.aop.RateLimitAspect;
import com.zsq.winter.redis.ddc.entity.AttributeVO;
import com.zsq.winter.redis.ddc.entity.Constants;
import com.zsq.winter.redis.ddc.listener.DynamicConfigCenterAdjustListener;
import com.zsq.winter.redis.ddc.service.*;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Winter Redis 统一配置类
 * <p>
 * 该类整合了所有Redis相关的配置，包括：
 * 1. Redis连接配置属性
 * 2. RedisTemplate配置
 * 3. RedissonClient配置
 * 4. Winter框架Redis组件配置
 * </p>
 * <p>
 * 通过合并多个配置类，简化了配置结构，提高了代码的可维护性。
 * </p>
 */
@Slf4j
@Configuration
//指定一个注解类，容器中必须存在至少一个带有该注解的 Bean，在注入LogAutoConfiguration
@EnableConfigurationProperties({WinterRedisAutoConfiguration.RedisProperties.class, WinterRedisAutoConfiguration.DynamicConfigCenterAutoProperties.class})
public class WinterRedisAutoConfiguration {

    @Data
    @ConfigurationProperties(prefix = "winter-redis-config", ignoreInvalidFields = true)
    public static class DynamicConfigCenterAutoProperties {

        /**
         * 系统名称
         */
        private String system;

        public String getKey(String attributeName) {
            return this.system + "_" + attributeName;
        }

    }

    /**
     * Redis连接属性配置类
     * <p>
     * 该类用于配置Redis连接属性，包括主机地址、端口、数据库、密码、连接池大小、最小空闲连接数、空闲连接超时、连接超时、重试次数、重试间隔时间、Ping连接间隔时间、是否保持连接。
     * </p>
     */
    @Data
    @ConfigurationProperties(prefix = "winter-redis-config.redission", ignoreInvalidFields = true)
    public static class RedisProperties {

        /**
         * Redis主机地址
         */
        private String host = "localhost";

        /**
         * Redis端口
         */
        private Integer port = 6379;

        /**
         * Redis数据库索引（0-15）
         */
        private Integer database = 0;

        /**
         * Redis密码
         */
        private String password;

        /**
         * 连接池大小
         */
        private Integer poolSize = 64;

        /**
         * 最小空闲连接数
         */
        private Integer minIdleSize = 10;

        /**
         * 空闲连接超时时间（毫秒）
         */
        private Integer idleTimeout = 10000;

        /**
         * 连接超时时间（毫秒）
         */
        private Integer connectTimeout = 10000;

        /**
         * 重试次数
         */
        private Integer retryAttempts = 3;

        /**
         * 重试间隔时间（毫秒）
         */
        private Integer retryInterval = 1500;

        /**
         * Ping连接间隔时间（毫秒）
         */
        private Integer pingInterval = 30000;

        /**
         * 是否保持连接
         */
        private boolean keepAlive = true;
    }


    /**
     * 创建并配置RedissonClient实例
     * <p>
     * 该方法根据配置属性创建RedissonClient实例，支持单机模式的Redis连接。
     * 使用JsonJacksonCodec作为默认的编解码器，支持JSON格式的数据序列化。
     * </p>
     *
     * @param properties Redis配置属性
     * @return RedissonClient实例
     */
    @Bean(value = "winterRedissonClient", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "winterRedissonClient")
    public RedissonClient redissonClient(RedisProperties properties) {
        Config config = new Config();
        // 根据需要可以设定编解码器；https://github.com/redisson/redisson/wiki/4.-%E6%95%B0%E6%8D%AE%E5%BA%8F%E5%88%97%E5%8C%96
        config.setCodec(JsonJacksonCodec.INSTANCE);

        config.useSingleServer()
                .setAddress("redis://" + properties.getHost() + ":" + properties.getPort())
                .setDatabase(properties.getDatabase())
                .setPassword(properties.getPassword().isEmpty() ? null : properties.getPassword())
                .setConnectionPoolSize(properties.getPoolSize())
                .setConnectionMinimumIdleSize(properties.getMinIdleSize())
                .setIdleConnectionTimeout(properties.getIdleTimeout())
                .setConnectTimeout(properties.getConnectTimeout())
                .setRetryAttempts(properties.getRetryAttempts())
                .setRetryInterval(properties.getRetryInterval())
                .setPingConnectionInterval(properties.getPingInterval())
                .setKeepAlive(properties.isKeepAlive());

        RedissonClient redissonClient = Redisson.create(config);

        log.info("注册器（redis）链接初始化完成。{} {} {}", properties.getHost(), properties.getPoolSize(), !redissonClient.isShutdown());

        return redissonClient;
    }

    /**
     * 创建并配置一个名为jacksonRedisTemplate的RedisTemplate bean
     * 该bean使用Jackson库来序列化和反序列化Redis中的数据
     * 主要用于处理复杂数据类型和对象的存储和检索
     */
    @Bean(name = "jacksonRedisTemplate")
    public RedisTemplate<String, Object> jacksonRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        // 配置Jackson2JsonRedisSerializer序列化器
        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(Object.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 添加对LocalDateTime序列化的支持
        objectMapper.registerModule(new JavaTimeModule());
        // 新的API：使用activateDefaultTyping指定序列化类
        //objectMapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
        // 通过该方法对mapper对象进行设置，所有序列化的对象都将按改规则进行系列化
        // Include.Include.ALWAYS 默认
        // Include.NON_DEFAULT 属性为默认值不序列化
        // Include.NON_EMPTY 属性为 空（""） 或者为 NULL 都不序列化
        // Include.NON_NULL 属性为NULL 不序列化
        // objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        // objectMapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT);
        // 配置序列化时的日期格式(yyyy-MM-dd)
        // objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        // 允许出现特殊字符和转义符
        // objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
        // 允许出现单引号
        // objectMapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        // 字段保留，序列化时将null值转为""
        serializer.setObjectMapper(objectMapper);

        // 配置StringRedisSerializer序列化器
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();

        // 使用StringRedisSerializer对redis key进行序列化，使用Jackson2JsonRedisSerializer对value进行序列化
        template.setKeySerializer(stringRedisSerializer);
        template.setHashKeySerializer(stringRedisSerializer);
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }


    /**
     * 创建并配置WinterRedisTemplate实例
     * <p>
     * 该方法负责创建WinterRedisTemplate实例并将其注册到Spring容器中。
     * WinterRedisTemplate是对Spring原生RedisTemplate的封装，提供了更加便捷的API和额外的功能。
     * </p>
     * <p>
     * 该Bean的创建受到以下条件控制：
     * 1. 只有当容器中不存在WinterRedisTemplate类型的Bean时才会创建（避免重复创建）
     * 2. 只有当容器中存在RedisTemplate类型的Bean时才会创建（确保依赖可用）
     * </p>
     *
     * @param jacksonRedisTemplate Jackson序列化的RedisTemplate
     * @return WinterRedisTemplate实例，用于执行Redis操作，提供了比原生RedisTemplate更便捷的API
     */
    @Bean
    @ConditionalOnMissingBean(WinterRedisTemplate.class)
    @ConditionalOnBean({RedisTemplate.class})
    public WinterRedisTemplate winterRedisTemplate(RedisTemplate<String, Object> jacksonRedisTemplate) {
        return new WinterRedisTemplate(jacksonRedisTemplate);
    }

    /**
     * 创建并配置WinterRedissionTemplate实例
     * <p>
     * 该方法负责创建WinterRedissionTemplate实例并将其注册到Spring容器中。
     * WinterRedissionTemplate是对Redisson的封装，提供了分布式锁和布隆过滤器等功能。
     * </p>
     * <p>
     * 该Bean的创建受到以下条件控制：
     * 1. 只有当容器中不存在WinterRedissionTemplate类型的Bean时才会创建（避免重复创建）
     * 2. 只有当容器中存在RedissonClient类型的Bean时才会创建（确保依赖可用）
     * </p>
     *
     * @param winterRedissonClient Redisson客户端
     * @return WinterRedissionTemplate实例，用于执行Redisson操作
     */
    @Bean
    @ConditionalOnMissingBean(WinterRedissionTemplate.class)
    @ConditionalOnBean({RedissonClient.class})
    public WinterRedissionTemplate winterRedissionTemplate(RedissonClient winterRedissonClient) {
        return new WinterRedissionTemplate(winterRedissonClient);
    }


    /**
     * 创建动态配置中心服务Bean
     * <p>
     * 该方法负责创建并注册IDynamicConfigCenterService实例到Spring容器中，
     * 用于处理动态配置的初始化和运行时调整功能。
     *
     * @param dynamicConfigCenterAutoProperties 动态配置中心自动配置属性，包含系统名称等配置信息
     * @param winterRedissionTemplate           Redisson操作模板，用于Redis相关操作
     * @return IDynamicConfigCenterService实例，提供动态配置服务功能
     */
    @Bean
    public IDynamicConfigCenterService dynamicConfigCenterService(DynamicConfigCenterAutoProperties dynamicConfigCenterAutoProperties, WinterRedissionTemplate winterRedissionTemplate) {
        return new DynamicConfigCenterService(dynamicConfigCenterAutoProperties, winterRedissionTemplate);
    }

    /**
     * 创建动态配置中心调整监听器Bean
     * <p>
     * 该方法负责创建并注册DynamicConfigCenterAdjustListener实例到Spring容器中，
     * 用于监听Redis中的配置变更消息并进行相应的处理。
     *
     * @param dynamicConfigCenterService 动态配置中心服务实例，用于处理配置调整逻辑
     * @return DynamicConfigCenterAdjustListener实例，监听配置变更消息
     */
    @Bean
    public DynamicConfigCenterAdjustListener dynamicConfigCenterAdjustListener(IDynamicConfigCenterService dynamicConfigCenterService) {
        return new DynamicConfigCenterAdjustListener(dynamicConfigCenterService);
    }

    /**
     * 创建动态配置中心Redis主题监听器Bean
     *
     * @param dynamicConfigCenterAutoProperties 动态配置中心自动配置属性，用于获取系统配置信息
     * @param dynamicConfigCenterAdjustListener 动态配置中心调整监听器，用于处理配置变更事件
     * @param winterRedissionTemplate           Redisson模板，用于创建和操作Redis主题
     * @return 配置好的RTopic对象，用于监听线程池配置调整消息
     */
    @Bean(name = "dynamicConfigCenterRedisTopic")
    public RTopic threadPoolConfigAdjustListener(DynamicConfigCenterAutoProperties dynamicConfigCenterAutoProperties,
                                                 DynamicConfigCenterAdjustListener dynamicConfigCenterAdjustListener, WinterRedissionTemplate winterRedissionTemplate) {
        // 获取指定系统的Redis主题并添加属性变更监听器
        RTopic topic = winterRedissionTemplate.getTopic(Constants.getTopic(dynamicConfigCenterAutoProperties.getSystem()));
        topic.addListener(AttributeVO.class, dynamicConfigCenterAdjustListener);
        return topic;
    }

    /**
     * 创建RateLimiterService实例的Bean
     * 当容器中不存在RateLimiterService类型的Bean时，该方法会被调用创建新的实例
     *
     * @param winterRedissionTemplate Redisson模板对象，用于实现限流功能的底层Redis操作
     * @return RateLimiterService实例
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimiterService rateLimiterService(WinterRedissionTemplate winterRedissionTemplate) {
        return new RateLimiterService(winterRedissionTemplate);
    }


    /**
     * 创建限流切面Bean
     *
     * @param rateLimiterService 限流服务实例，用于执行具体的限流逻辑
     * @return RateLimitAspect 限流切面实例，用于处理@RateLimit注解
     */
    @Bean
    @ConditionalOnMissingBean
    public RateLimitAspect rateLimitAspect(RateLimiterService rateLimiterService) {
        return new RateLimitAspect(rateLimiterService);
    }


}