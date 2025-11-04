package com.zsq.winter.redis.ddc.service;


import com.zsq.winter.redis.ddc.entity.AttributeVO;

/**
 * 动态配置中心服务接口
 */
public interface IDynamicConfigCenterService {

    /**
     * 代理对象处理方法
     *
     * 用于扫描Bean对象中的@DCCValue注解字段，初始化配置值并建立映射关系
     *
     * @param bean 需要处理的Bean对象
     * @return 处理后的Bean对象
     */
    Object proxyObject(Object bean);

    /**
     * 调整属性值
     *
     * 当接收到配置变更通知时，更新Redis中的配置值并同步到对应的Bean对象字段
     *
     * @param attributeVO 属性值对象，包含属性名和新值
     */
    void adjustAttributeValue(AttributeVO attributeVO);

}

