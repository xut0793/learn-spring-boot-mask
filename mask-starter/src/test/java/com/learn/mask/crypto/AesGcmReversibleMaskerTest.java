package com.learn.mask.crypto;

import com.learn.mask.config.MaskingProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AesGcmReversibleMaskerTest {

    @Test
    void encryptAndDecryptRoundTrip() {
        AesGcmReversibleMasker masker = new AesGcmReversibleMasker(new MaskingProperties());
        String cipher = masker.encrypt("13812345678");
        assertThat(cipher).isNotEqualTo("13812345678");
        assertThat(masker.decrypt(cipher)).isEqualTo("13812345678");
    }
}
