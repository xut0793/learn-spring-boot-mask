package com.learn.mask.crypto;

import com.learn.mask.config.MaskingProperties;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmReversibleMaskerTest {

    @Test
    void encryptAndDecryptRoundTrip() {
        AesGcmReversibleMasker masker = new AesGcmReversibleMasker(new MaskingProperties());
        String cipher = masker.encrypt("13812345678");
        assertThat(cipher).isNotEqualTo("13812345678");
        assertThat(masker.decrypt(cipher)).isEqualTo("13812345678");
    }

    @Test
    void samePlaintextYieldsDifferentTokens() {
        AesGcmReversibleMasker masker = new AesGcmReversibleMasker(new MaskingProperties());
        String first = masker.encrypt("13812345678");
        String second = masker.encrypt("13812345678");
        assertThat(first).isNotEqualTo(second);
        assertThat(masker.decrypt(first)).isEqualTo("13812345678");
    }

    @Test
    void tamperedTokenFails() {
        AesGcmReversibleMasker masker = new AesGcmReversibleMasker(new MaskingProperties());
        String token = masker.encrypt("13812345678");
        byte[] bytes = Base64.getDecoder().decode(token);
        bytes[bytes.length - 1] ^= 0x01;
        assertThatThrownBy(() -> masker.decrypt(Base64.getEncoder().encodeToString(bytes)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shortKeyIsRejected() {
        assertThatThrownBy(() -> AesGcmReversibleMasker.secretKeySpec("short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("16 or 32");
    }
}
