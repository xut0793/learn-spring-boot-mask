package com.learn.mask.demo.mask;

import com.learn.mask.mybatis.SensitiveTypeHandler;

/**
 * 快递单号 MyBatis TypeHandler。编码 {@link DemoSensitiveTypes#EXPRESS} 仅存在于 demo，starter 枚举无需改动。
 */
public class ExpressSensitiveTypeHandler extends SensitiveTypeHandler {

    public ExpressSensitiveTypeHandler() {
        super(DemoSensitiveTypes.EXPRESS);
    }
}
