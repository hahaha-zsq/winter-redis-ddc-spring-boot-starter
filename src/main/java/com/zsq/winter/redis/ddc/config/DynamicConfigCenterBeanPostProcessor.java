package com.zsq.winter.redis.ddc.config;

import com.zsq.winter.redis.ddc.service.IDynamicConfigCenterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;

/**
 * 动态配置中心Bean后处理器
 * <p>
 * 该类专门负责处理动态配置相关的Bean后处理逻辑，
 * 通过构造函数注入IDynamicConfigCenterService来处理Bean的动态代理包装。
 * </p>
 * <p>
 * 主要功能：
 * 1. 扫描Bean对象中的@DCCValue注解字段
 * 2. 初始化配置值并建立映射关系
 * 3. 对Bean进行动态代理包装处理
 * </p>
 */
@Slf4j
@Configuration
public class DynamicConfigCenterBeanPostProcessor implements BeanPostProcessor {

    private final IDynamicConfigCenterService dynamicConfigCenterService;

    public DynamicConfigCenterBeanPostProcessor(IDynamicConfigCenterService dynamicConfigCenterService) {
        this.dynamicConfigCenterService = dynamicConfigCenterService;
    }

    /**
     * 在Bean初始化完成后进行后处理操作
     * <p>
     * 对Bean进行动态代理包装处理。
     * </p>
     *
     * @param bean     需要被处理的Bean实例
     * @param beanName Bean的名称
     * @return 处理后的Bean实例，通常是对原Bean进行动态代理包装后的对象
     * @throws BeansException 当Bean处理过程中发生错误时抛出
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        try {
            // 对Bean进行动态代理包装处理
            return dynamicConfigCenterService.proxyObject(bean);
        } catch (Exception e) {
            log.error("处理Bean [{}] 时发生错误", beanName, e);
            // 如果处理失败，返回原始Bean
            return bean;
        }
    }
}