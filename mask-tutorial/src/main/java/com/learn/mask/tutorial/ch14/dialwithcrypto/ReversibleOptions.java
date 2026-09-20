package com.learn.mask.tutorial.ch14.dialwithcrypto;

/**
 * 外呼场景 AES 配置（本包自包含，与 {@code viewwithcrypto} 包中类同名但互不依赖）。
 *
 * @param enabled   可逆通道开关
 * @param secretKey 演示密钥字符串，经补零/截断派生 AES-256 密钥
 */
public record ReversibleOptions(boolean enabled, String secretKey) {

    /** 与 demo/starter 默认一致的 27 字符演示钥。 */
    public static final String DEMO_KEY = "demo-key-not-for-prod-32b!!";

    public static ReversibleOptions demo() {
        return new ReversibleOptions(true, DEMO_KEY);
    }

    public ReversibleOptions sanitized() {
        String key = secretKey == null ? "" : secretKey;
        return new ReversibleOptions(enabled, key);
    }
}
