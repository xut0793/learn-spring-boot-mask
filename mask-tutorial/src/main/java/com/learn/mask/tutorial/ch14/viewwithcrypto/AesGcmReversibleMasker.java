package com.learn.mask.tutorial.ch14.viewwithcrypto;

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
 * AES-GCM 封装票面声明（非手机号本身）。
 * <p>
 *  wire 格式：{@code Base64(12 字节 IV || ciphertext || 16 字节 GCM tag)}。
 * 随机 IV 导致同一票面两次加密结果不同，故 token 不能进第 8 章脱敏缓存。
 */
public class AesGcmReversibleMasker {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int AES_KEY_LENGTH = 32;

    /** 密钥与 enabled 开关来源。 */
    private final ReversibleOptions options;

    /** 每次加密生成新 IV。 */
    private final SecureRandom random = new SecureRandom();

    /**
     * @param options 密钥配置，内部会 {@link ReversibleOptions#sanitized()}
     */
    public AesGcmReversibleMasker(ReversibleOptions options) {
        this.options = options.sanitized();
    }

    /**
     * 加密 UTF-8 票面字符串。
     *
     * @param plainText 序列化后的 {@code userId + field + expires + purpose}
     * @return Base64 token，{@code null} 入参返回 {@code null}
     */
    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherBytes.length);
            buffer.put(iv);
            buffer.put(cipherBytes);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to encrypt ticket", ex);
        }
    }

    /**
     * 解密 token 得到票面明文；篡改或密钥错误时 fail-loud。
     *
     * @param cipherText Base64 token
     * @return 票面字符串
     */
    public String decrypt(String cipherText) {
        if (cipherText == null) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(cipherText);
            if (decoded.length <= GCM_IV_LENGTH) {
                throw new IllegalStateException("Failed to decrypt ticket");
            }
            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] cipherBytes = new byte[buffer.remaining()];
            buffer.get(cipherBytes);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Failed to decrypt ticket", ex);
        }
    }

    /**
     * 暴露派生后的 32 字节 AES 密钥，供单测验证「短钥补零」行为（与 starter 一致）。
     */
    public byte[] rawKeyBytes() {
        byte[] keyBytes = options.secretKey().getBytes(StandardCharsets.UTF_8);
        byte[] aesKey = new byte[AES_KEY_LENGTH];
        System.arraycopy(keyBytes, 0, aesKey, 0, Math.min(keyBytes.length, AES_KEY_LENGTH));
        return aesKey;
    }

    private SecretKey secretKey() {
        return new SecretKeySpec(rawKeyBytes(), "AES");
    }
}
