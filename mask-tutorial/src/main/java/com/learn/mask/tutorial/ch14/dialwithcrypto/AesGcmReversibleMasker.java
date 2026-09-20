package com.learn.mask.tutorial.ch14.dialwithcrypto;

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
 * 外呼票面 AES-GCM 加解密，格式 {@code Base64(IV || ciphertext || tag)}。
 * <p>
 * 加密内容是「谁、哪字段、何时过期、DIAL」声明，不是手机号。
 */
public class AesGcmReversibleMasker {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int AES_KEY_LENGTH = 32;

    private final ReversibleOptions options;

    private final SecureRandom random = new SecureRandom();

    public AesGcmReversibleMasker(ReversibleOptions options) {
        this.options = options.sanitized();
    }

    /**
     * @param plainText 序列化票面
     * @return 可作为 HTTP 响应 {@code token} 字段的 Base64 串
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
     * @param cipherText 签发时返回的 token
     * @return 解密后的票面字符串
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

    private SecretKey secretKey() {
        byte[] keyBytes = options.secretKey().getBytes(StandardCharsets.UTF_8);
        byte[] aesKey = new byte[AES_KEY_LENGTH];
        System.arraycopy(keyBytes, 0, aesKey, 0, Math.min(keyBytes.length, AES_KEY_LENGTH));
        return new SecretKeySpec(aesKey, "AES");
    }
}
