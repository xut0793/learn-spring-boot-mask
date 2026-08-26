package com.learn.mask.crypto;

/**
 * 可逆脱敏：接口仍输出星号，客服还原时用 AES 令牌换回明文（明文来自库，不把密文写进 JSON）。
 */
public interface ReversibleMasker {

    String encrypt(String plainText);

    String decrypt(String cipherText);
}
