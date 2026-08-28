package com.learn.mask.tutorial.ch14;

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
 * AES-GCM 可逆实现。密文格式 {@code Base64(IV + ciphertext + tag)}。
 * <p>
 * IV 每次随机 12 字节，所以同一明文每次加密结果都不同——这也是它<strong>不能进脱敏缓存</strong>的原因。
 * 密钥处理与 starter 一致：不足 32 字节补 0、超过则截断。第 14.5 节会批评这个做法。
 */
public class AesGcmReversibleMasker implements ReversibleMasker {

    static final String TRANSFORMATION = "AES/GCM/NoPadding";
    static final int GCM_IV_LENGTH = 12;
    static final int GCM_TAG_BITS = 128;
    static final int AES_KEY_LENGTH = 32;

    private final ReversibleOptions options;
    private final SecureRandom random = new SecureRandom();

    public AesGcmReversibleMasker(ReversibleOptions options) {
        this.options = options.sanitized();
    }

    @Override
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
            throw new IllegalStateException("Failed to encrypt reversible field", ex);
        }
    }

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
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Failed to decrypt reversible field", ex);
        }
    }

    /**
     * 与 starter 相同：按 UTF-8 字节补 0 或截断到 32。
     * 短密钥有效熵被稀释，长密钥被静默丢掉——生产不该这样。
     */
    byte[] rawKeyBytes() {
        byte[] keyBytes = options.secretKey().getBytes(StandardCharsets.UTF_8);
        byte[] aesKey = new byte[AES_KEY_LENGTH];
        System.arraycopy(keyBytes, 0, aesKey, 0, Math.min(keyBytes.length, AES_KEY_LENGTH));
        return aesKey;
    }

    private SecretKey secretKey() {
        return new SecretKeySpec(rawKeyBytes(), "AES");
    }
}
