package com.learn.mask.demo.mask;

import com.learn.mask.config.MaskRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AddressMaskStrategyTest {

    private final AddressMaskStrategy strategy = new AddressMaskStrategy();
    private final MaskRule rule = addressRule();

    @Test
    void masksTrailingHouseNumber() {
        assertThat(strategy.mask("Chaoyang Road 88", rule)).isEqualTo("Chaoyang Road **");
        assertThat(strategy.mask("朝阳路88号", rule)).isEqualTo("朝阳路**号");
    }

    @Test
    void fallsBackToKeepPrefixWhenNoHouseNumber() {
        assertThat(strategy.mask("Zhongguancun", rule)).isEqualTo("Zho*********");
    }

    @Test
    void skipsAlreadyMaskedAddress() {
        assertThat(strategy.alreadyMasked("Chaoyang Road **", rule)).isTrue();
        assertThat(strategy.alreadyMasked("朝阳路**号", rule)).isTrue();
        assertThat(strategy.alreadyMasked("Chaoyang Road 88", rule)).isFalse();
    }

    private static MaskRule addressRule() {
        MaskRule maskRule = new MaskRule();
        maskRule.setKeepPrefix(3);
        maskRule.setKeepSuffix(0);
        return maskRule;
    }
}
