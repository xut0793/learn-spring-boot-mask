package com.learn.mask.demo.mask;

import com.learn.mask.config.MaskRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExpressNoMaskStrategyTest {

    private final ExpressNoMaskStrategy strategy = new ExpressNoMaskStrategy();
    private final MaskRule rule = expressRule();

    @Test
    void masksDigitsAfterCarrierPrefix() {
        assertThat(strategy.mask("SF1234567890123", rule)).isEqualTo("SF12*******0123");
    }

    @Test
    void keepsTrailingCountryCode() {
        assertThat(strategy.mask("EA123456789CN", rule)).isEqualTo("EA12***6789CN");
    }

    @Test
    void masksPureDigitTrackingNo() {
        assertThat(strategy.mask("7730123456789", rule)).isEqualTo("77*******6789");
    }

    @Test
    void skipsAlreadyMaskedExpressNo() {
        assertThat(strategy.alreadyMasked("SF12*******0123", rule)).isTrue();
        assertThat(strategy.alreadyMasked("SF1234567890123", rule)).isFalse();
    }

    private static MaskRule expressRule() {
        return MaskRule.of(2, 4);
    }
}
