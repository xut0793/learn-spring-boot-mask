package com.learn.mask.tutorial.ch14;

/**
 * 线程 B 的 AES 能力：签发 / 核销查看凭证。
 * <p>
 * 明文始终来自存储，不是把星号解密回去，也不把密文写进展示 JSON。
 * 没有限时票据需求时用 {@code UnmaskService.withoutCrypto()}，不必实现本接口。
 * 见第 14.3、14.5 节。
 */
public interface ReversibleMasker {

    String encrypt(String plainText);

    String decrypt(String cipherText);
}
