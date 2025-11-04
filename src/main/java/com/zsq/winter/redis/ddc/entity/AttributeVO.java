package com.zsq.winter.redis.ddc.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class AttributeVO {

    /**
     * 键 - 属性 fileName
     */
    private String attribute;

    /**
     * 值
     */
    private String value;
}
