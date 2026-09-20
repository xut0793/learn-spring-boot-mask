package com.learn.mask.crypto;

/**
 * 可逆脱敏：接口仍输出星号，客服还原时用 AES 令牌换回明文（明文来自库，不把密文写进 JSON）。
 */
public interface ReversibleMasker {

    /** 将票据载荷或类似短文本加密为 Base64 密文。 */
    String encrypt(String plainText);

    /** 解密 {@link #encrypt(String)} 的输出。 */
    String decrypt(String cipherText);
}
