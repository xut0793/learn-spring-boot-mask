package com.learn.mask.crypto;

import com.learn.mask.config.MaskingProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmReversibleMaskerTest {

    private final AesGcmReversibleMasker masker = new AesGcmReversibleMasker(new MaskingProperties());

    @Nested
    @DisplayName("加解密")
    class RoundTrip {

        @Test
        @DisplayName("同一明文能解回原文")
        void encryptAndDecryptRoundTrip() {
            String cipher = masker.encrypt("13812345678");
            assertThat(cipher).isNotEqualTo("13812345678");
            assertThat(masker.decrypt(cipher)).isEqualTo("13812345678");
        }

        @Test
        @DisplayName("同一明文两次加密结果不同 —— IV 是随机的，所以不能缓存密文")
        void samePlaintextYieldsDifferentTokens() {
            String first = masker.encrypt("13812345678");
            String second = masker.encrypt("13812345678");
            assertThat(first).isNotEqualTo(second);
            assertThat(masker.decrypt(first)).isEqualTo("13812345678");
            assertThat(masker.decrypt(second)).isEqualTo("13812345678");
        }

        @Test
        @DisplayName("null 原样透传")
        void nullsPassThrough() {
            assertThat(masker.encrypt(null)).isNull();
            assertThat(masker.decrypt(null)).isNull();
        }

        @Test
        @DisplayName("不同密钥解不开")
        void differentKeysCannotDecrypt() {
            String token = masker.encrypt("13812345678");
            AesGcmReversibleMasker other = new AesGcmReversibleMasker("another-key-not-for-prod-use!!!!");
            assertThatThrownBy(() -> other.decrypt(token)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("完整性与密钥长度")
    class IntegrityAndKey {

        @Test
        @DisplayName("篡改密文会失败，而不是解出乱码")
        void tamperedTokenFails() {
            String token = masker.encrypt("13812345678");
            byte[] bytes = Base64.getDecoder().decode(token);
            bytes[bytes.length - 1] ^= 0x01;
            assertThatThrownBy(() -> masker.decrypt(Base64.getEncoder().encodeToString(bytes)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("decrypt");
        }

        @Test
        @DisplayName("短密钥直接拒绝，不会补 0 或截断")
        void shortKeyIsRejected() {
            assertThatThrownBy(() -> AesGcmReversibleMasker.secretKeySpec("short"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("16 or 32");
        }

        @Test
        @DisplayName("17 字节同样拒绝 —— 必须正好 16 或 32")
        void seventeenByteKeyIsRejected() {
            assertThatThrownBy(() -> AesGcmReversibleMasker.secretKeySpec("12345678901234567"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("got 17");
        }

        @Test
        @DisplayName("16 字节密钥可以工作")
        void sixteenByteKeyWorks() {
            AesGcmReversibleMasker aes = new AesGcmReversibleMasker("1234567890123456");
            assertThat(aes.decrypt(aes.encrypt("hello"))).isEqualTo("hello");
        }
    }
}
