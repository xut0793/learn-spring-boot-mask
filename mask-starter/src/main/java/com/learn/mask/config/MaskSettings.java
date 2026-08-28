package com.learn.mask.config;

/**
 * 引擎需要从配置里知道的全部信息：总开关和按编码取规则。
 * <p>
 * 引擎依赖本接口而不是 {@link MaskingProperties}，测试不必构造完整配置对象。
 */
public interface MaskSettings {

    /** 脱敏总开关。关闭时引擎记 {@code DISABLED} 并返回原文。 */
    boolean isEnabled();

    /**
     * 查某个类型编码对应的规则。
     * 未命中时应回落 {@code CUSTOM}，避免漏配时返回明文。
     */
    MaskRule ruleOf(String code);
}
