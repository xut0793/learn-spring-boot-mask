package com.learn.mask.tutorial.ch14;

/**
 * 可逆通道配置。默认密钥只给 Demo / 测试用，生产必须换成 KMS 或 Vault 下发的 32 字节密钥。
 */
public record ReversibleOptions(boolean enabled, String secretKey) {

    public static final String DEMO_KEY = "demo-key-not-for-prod-32b!!";

    public static ReversibleOptions demo() {
        return new ReversibleOptions(true, DEMO_KEY);
    }

    public ReversibleOptions sanitized() {
        String key = secretKey == null ? "" : secretKey;
        return new ReversibleOptions(enabled, key);
    }
}
