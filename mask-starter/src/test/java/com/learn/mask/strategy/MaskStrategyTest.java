package com.learn.mask.strategy;

import com.learn.mask.config.MaskRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaskStrategyTest {

    private final MaskRule phoneRule = rule(3, 4);
    private final MaskRule idRule = rule(6, 4);
    private final MaskRule bankRule = rule(4, 4);
    private final MaskRule emailRule = rule(1, 0);

    @Test
    void phoneMask() {
        PhoneMaskStrategy strategy = new PhoneMaskStrategy();
        assertThat(strategy.mask("13812345678", phoneRule)).isEqualTo("138****5678");
        assertThat(strategy.alreadyMasked("138****5678", phoneRule)).isTrue();
        assertThat(strategy.alreadyMasked("13812345678", phoneRule)).isFalse();
    }

    @Test
    void idCardMask() {
        IdCardMaskStrategy strategy = new IdCardMaskStrategy();
        assertThat(strategy.mask("110101199003078515", idRule)).isEqualTo("110101********8515");
    }

    @Test
    void bankCardMask() {
        BankCardMaskStrategy strategy = new BankCardMaskStrategy();
        assertThat(strategy.mask("6222021234567890123", bankRule)).isEqualTo("6222***********0123");
    }

    @Test
    void emailMask() {
        EmailMaskStrategy strategy = new EmailMaskStrategy();
        assertThat(strategy.mask("zhangsan@example.com", emailRule)).isEqualTo("z*******@example.com");
        assertThat(strategy.alreadyMasked("z*******@example.com", emailRule)).isTrue();
    }

    @Test
    void shortValueFullyMasked() {
        CustomPatternMaskStrategy strategy = new CustomPatternMaskStrategy();
        assertThat(strategy.mask("ab", rule(3, 4))).isEqualTo("**");
    }

    private static MaskRule rule(int prefix, int suffix) {
        MaskRule rule = new MaskRule();
        rule.setKeepPrefix(prefix);
        rule.setKeepSuffix(suffix);
        return rule;
    }
}
