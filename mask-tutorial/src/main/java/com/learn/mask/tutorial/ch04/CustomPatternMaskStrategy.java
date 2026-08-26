package com.learn.mask.tutorial.ch04;

/**
 * 通用策略，也是策略表未命中时的回落目标。
 * <p>
 * 它的存在保证了一件事：**任何类型编码都能查到一个策略**，调用方不需要处理 null。
 */
public class CustomPatternMaskStrategy extends AbstractKeepMaskStrategy {

    @Override
    public SensitiveType type() {
        return SensitiveType.CUSTOM;
    }
}
