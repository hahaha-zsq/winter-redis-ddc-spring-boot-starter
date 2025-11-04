package com.zsq.winter.redis.ddc.service;


import com.zsq.winter.redis.ddc.annotation.DCCValue;
import com.zsq.winter.redis.ddc.config.WinterRedisAutoConfiguration;
import com.zsq.winter.redis.ddc.entity.AttributeVO;
import com.zsq.winter.redis.ddc.entity.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.util.ObjectUtils;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 动态配置中心服务实现类
 * <p>
 * 负责处理动态配置的初始化和运行时调整，通过Redis实现配置的分布式同步
 */
@Slf4j
public class DynamicConfigCenterService implements IDynamicConfigCenterService {

    /**
     * 动态配置中心自动配置属性
     */
    private final WinterRedisAutoConfiguration.DynamicConfigCenterAutoProperties properties;

    /**
     * Redisson模板工具类，用于Redis操作
     */
    private final WinterRedissionTemplate winterRedissionTemplate;

    /**
     * DCC Bean分组映射表，用于存储配置key与对应Bean对象的映射关系
     * key: Redis配置key
     * value: 对应的Bean对象
     */
    private final Map<String, Object> dccBeanGroup = new ConcurrentHashMap<>();

    /**
     * 构造函数
     *
     * @param properties              动态配置中心属性配置
     * @param winterRedissionTemplate Redisson操作模板
     */
    public DynamicConfigCenterService(WinterRedisAutoConfiguration.DynamicConfigCenterAutoProperties properties, WinterRedissionTemplate winterRedissionTemplate) {
        this.properties = properties;
        this.winterRedissionTemplate = winterRedissionTemplate;
    }

    /**
     * 代理对象处理方法
     * <p>
     * 扫描Bean对象中的@DCCValue注解字段，初始化配置值并建立映射关系
     *
     * @param bean 需要处理的Bean对象
     * @return 处理后的Bean对象
     */
    @Override
    public Object proxyObject(Object bean) {
        // 注意；增加 AOP 代理后，获得类的方式要通过 AopProxyUtils.getTargetClass(bean); 不能直接 bean.class 因为代理后类的结构发生变化，获取到的是动态代理的对象，这样不能获得到自己的自定义注解了。
        Class<?> targetBeanClass = bean.getClass();
        Object targetBeanObject = bean;
        // 判断是否为AOP代理对象，如果是则获取目标类和目标对象
        if (AopUtils.isAopProxy(bean)) {
            // 获取代理对象的目标类，用于后续的字段反射操作
            targetBeanClass = AopUtils.getTargetClass(bean);
            // 获取代理对象的单例目标对象，用于设置字段值
            targetBeanObject = AopProxyUtils.getSingletonTarget(bean);
        }

        // 获取目标类声明的所有字段
        Field[] fields = targetBeanClass.getDeclaredFields();
        for (Field field : fields) {
            // 过滤没有@DCCValue注解的字段
            if (!field.isAnnotationPresent(DCCValue.class)) {
                continue;
            }

            // 获取字段上的@DCCValue注解
            DCCValue dccValue = field.getAnnotation(DCCValue.class);

            // 获取注解配置的值（格式：key:defaultValue）
            String value = dccValue.value();
            if (ObjectUtils.isEmpty(value)) {
                throw new RuntimeException(field.getName() + " @DCCValue is not config value config case 「isSwitch/isSwitch:1」");
            }

            // 解析配置值，分割key和默认值
            String[] splits = value.split(Constants.SYMBOL_COLON);
            String key = properties.getKey(splits[0].trim());

            // 提取默认值（如果有）
            String defaultValue = splits.length == 2 ? splits[1] : null;

            // 设置值，默认使用默认值
            String setValue = defaultValue;

            try {
                // 如果为空则抛出异常，必须配置默认值
                if (ObjectUtils.isEmpty(defaultValue)) {
                    throw new RuntimeException("dcc config error " + key + " is not null - 请配置默认值！");
                }

                // Redis 操作，判断配置Key是否存在，不存在则创建，存在则获取最新值
                boolean exists = winterRedissionTemplate.isExists(key);
                if (!exists) {
                    // Key不存在，使用默认值初始化
                    winterRedissionTemplate.set(key, defaultValue);
                } else {
                    // Key存在，获取当前值
                    setValue = winterRedissionTemplate.get(key);
                }

                // 通过反射设置字段值
                field.setAccessible(true);
                field.set(targetBeanObject, setValue);
                field.setAccessible(false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // 将配置key与Bean对象建立映射关系，用于后续动态更新
            dccBeanGroup.put(key, targetBeanObject);
        }

        return bean;
    }

    /**
     * 调整属性值方法
     * <p>
     * 当接收到配置变更通知时，更新Redis中的配置值并同步到对应的Bean对象字段
     *
     * @param attributeVO 属性值对象，包含属性名和新值
     */
    @Override
    public void adjustAttributeValue(AttributeVO attributeVO) {
        // 属性信息
        String key = properties.getKey(attributeVO.getAttribute());
        String value = attributeVO.getValue();

        // 设置值到Redis
        boolean exists = winterRedissionTemplate.isExists(key);
        if (!exists) return;
        winterRedissionTemplate.set(key, attributeVO.getValue());

        // 从映射表中获取对应的Bean对象
        Object objBean = dccBeanGroup.get(key);
        if (null == objBean) return;

        Class<?> objBeanClass = objBean.getClass();
        // 检查 objBean 是否是代理对象
        if (AopUtils.isAopProxy(objBean)) {
            // 获取代理对象的目标对象
            objBeanClass = AopUtils.getTargetClass(objBean);
        }

        try {
            // 1. getDeclaredField 方法用于获取指定类中声明的所有字段，包括私有字段、受保护字段和公共字段。
            // 2. getField 方法用于获取指定类中的公共字段，即只能获取到公共访问修饰符（public）的字段。
            // 通过属性名获取对应的字段
            Field field = objBeanClass.getDeclaredField(attributeVO.getAttribute());
            // 设置字段可访问并更新值
            field.setAccessible(true);
            field.set(objBean, value);
            field.setAccessible(false);

            log.info("DCC 节点监听，动态设置值 {} {}", key, value);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
