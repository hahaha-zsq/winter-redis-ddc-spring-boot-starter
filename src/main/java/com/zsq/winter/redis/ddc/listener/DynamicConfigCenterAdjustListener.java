package com.zsq.winter.redis.ddc.listener;


import com.zsq.winter.redis.ddc.entity.AttributeVO;
import com.zsq.winter.redis.ddc.service.IDynamicConfigCenterService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.listener.MessageListener;

/**
 * <h2>动态配置中心调整监听器<h2/>
 * <p>
 * 使用redission的订阅模式的监听器用于监听Redis中的配置变更消息，并调用服务层进行相应的配置调整
 * </p>
 */
@Slf4j
public class DynamicConfigCenterAdjustListener implements MessageListener<AttributeVO> {

    /**
     * 动态配置中心服务接口
     */
    private final IDynamicConfigCenterService dynamicConfigCenterService;

    /**
     * 构造函数
     *
     * @param dynamicConfigCenterService 动态配置中心服务实例
     */
    public DynamicConfigCenterAdjustListener(IDynamicConfigCenterService dynamicConfigCenterService) {
        this.dynamicConfigCenterService = dynamicConfigCenterService;
    }

    /**
     * 消息监听回调方法
     * <p>
     * 当接收到配置变更消息时，会调用此方法处理
     *
     * @param charSequence 频道名称
     * @param attributeVO  配置属性对象，包含变更的属性名和值
     */
    @Override
    public void onMessage(CharSequence charSequence, AttributeVO attributeVO) {
        try {
            // 记录配置变更日志
            log.info("dcc config attribute:{} value:{}", attributeVO.getAttribute(), attributeVO.getValue());
            // 调用服务层方法调整配置属性值
            dynamicConfigCenterService.adjustAttributeValue(attributeVO);
        } catch (Exception e) {
            // 记录配置调整异常日志
            log.error("dcc config attribute:{} value:{}", attributeVO.getAttribute(), attributeVO.getValue(), e);
        }
    }
}
