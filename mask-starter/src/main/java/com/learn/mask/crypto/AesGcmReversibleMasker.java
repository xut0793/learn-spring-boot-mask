package com.learn.mask.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM 可逆脱敏实现。密文为 Base64(IV + ciphertext)，密钥必须是 16 或 32 个 UTF-8 字节。
 */
public class AesGcmReversibleMasker implements ReversibleMasker {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKey secretKey;
    private final SecureRandom random = new SecureRandom();

    /** 从 UTF-8 密钥字符串构造；长度非法时抛出 {@link IllegalStateException}。 */
    public AesGcmReversibleMasker(String secretKey) {
        this.secretKey = secretKeySpec(secretKey);
    }

    /** 读取 {@code masking.reversible.secret-key}。 */
    public AesGcmReversibleMasker(com.learn.mask.config.MaskingProperties properties) {
        this(properties.getReversible().getSecretKey());
    }

    /** 校验密钥长度并包装为 {@link SecretKeySpec}。 */
    static SecretKey secretKeySpec(String secretKey) {
        byte[] keyBytes = secretKey == null ? new byte[0] : secretKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 16 && keyBytes.length != 32) {
            throw new IllegalStateException(
                    "masking.reversible.secret-key must be 16 or 32 UTF-8 bytes, got " + keyBytes.length);
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    /** 随机 IV + GCM 加密，输出 Base64(IV || ciphertext)。 */
    @Override
    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherBytes.length);
            buffer.put(iv);
            buffer.put(cipherBytes);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to encrypt reversible field", ex);
        }
    }

    /** 解析 Base64 并验签解密；格式或 MAC 错误时抛出 {@link IllegalStateException}。 */
    @Override
    public String decrypt(String cipherText) {
        if (cipherText == null) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(cipherText);
            if (decoded.length <= GCM_IV_LENGTH) {
                throw new IllegalStateException("Failed to decrypt reversible field");
            }
            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] cipherBytes = new byte[buffer.remaining()];
            buffer.get(cipherBytes);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Failed to decrypt reversible field", ex);
        }
    }
}
