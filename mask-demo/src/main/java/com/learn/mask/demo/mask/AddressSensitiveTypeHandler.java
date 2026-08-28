package com.learn.mask.demo.mask;

import com.learn.mask.mybatis.SensitiveTypeHandler;

/**
 * 地址 MyBatis TypeHandler。编码 {@link AddressMaskStrategy#ADDRESS} 仅存在于 demo，starter 枚举无需改动。
 */
public class AddressSensitiveTypeHandler extends SensitiveTypeHandler {

    public AddressSensitiveTypeHandler() {
        super(AddressMaskStrategy.ADDRESS);
    }
}
