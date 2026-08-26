package com.learn.mask.demo.mask;

/**
 * 业务侧自定义脱敏类型编码。不必改 starter 的 {@code SensitiveType} 枚举。
 * <p>
 * 与 {@link ExpressNoMaskStrategy#code()}、{@code @Sensitive(code)}、{@code masking.rules.extras} 使用同一字符串。
 */
public final class DemoSensitiveTypes {

    /** 快递单号。 */
    public static final String EXPRESS = "EXPRESS";

    private DemoSensitiveTypes() {
    }
}
