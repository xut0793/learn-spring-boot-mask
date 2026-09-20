package com.learn.mask.tutorial.ch14.viewwithcrypto;

/**
 * 可逆通道 AES 配置。教程默认钥仅用于本地/demo，生产须 KMS/Vault 下发 32 字节密钥。
 *
 * @param enabled   是否启用可逆通道（教程代码未据此短路，与 starter 配置对齐）
 * @param secretKey 口令字符串，{@link AesGcmReversibleMasker} 会按 UTF-8 补零/截断到 32 字节
 */
public record ReversibleOptions(boolean enabled, String secretKey) {

    /** 与 mask-demo 默认配置一致的演示密钥（27 字符，靠补零凑满 32 字节）。 */
    public static final String DEMO_KEY = "demo-key-not-for-prod-32b!!";

    /** 教程/单测用的默认选项。 */
    public static ReversibleOptions demo() {
        return new ReversibleOptions(true, DEMO_KEY);
    }

    /** 避免 {@code null} 密钥传入加密器。 */
    public ReversibleOptions sanitized() {
        String key = secretKey == null ? "" : secretKey;
        return new ReversibleOptions(enabled, key);
    }
}
