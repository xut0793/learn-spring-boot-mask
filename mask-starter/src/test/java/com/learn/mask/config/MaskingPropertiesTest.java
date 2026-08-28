package com.learn.mask.config;

import com.learn.mask.annotation.SensitiveType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaskingPropertiesTest {

    private final MaskingProperties properties = new MaskingProperties();

    @Test
    void defaultRulesMatchBuiltins() {
        assertThat(properties.ruleOf(SensitiveType.PHONE)).isEqualTo(MaskRule.of(3, 4));
        assertThat(properties.ruleOf(SensitiveType.ID_CARD)).isEqualTo(MaskRule.of(6, 4));
        assertThat(properties.ruleOf(SensitiveType.BANK_CARD)).isEqualTo(MaskRule.of(4, 4));
        assertThat(properties.ruleOf(SensitiveType.EMAIL)).isEqualTo(MaskRule.of(1, 0));
        assertThat(properties.ruleOf(SensitiveType.CUSTOM)).isEqualTo(MaskRule.of(1, 1));
    }

    @Test
    void lookupIsNormalized() {
        assertThat(properties.ruleOf("phone")).isEqualTo(properties.ruleOf("PHONE"));
        assertThat(properties.ruleOf("  Phone ")).isEqualTo(properties.ruleOf("PHONE"));
    }

    @Test
    void unknownCodeFallsBackToCustom() {
        assertThat(properties.ruleOf("PASSPORT")).isEqualTo(properties.ruleOf(SensitiveType.CUSTOM));
    }

    @Test
    void mutatingRuleSetDoesNotLeakBeforeRebuild() {
        properties.getRules().getPhone().setKeepPrefix(0);

        assertThat(properties.ruleOf(SensitiveType.PHONE).keepPrefix()).isEqualTo(3);

        properties.rebuildSnapshot();

        assertThat(properties.ruleOf(SensitiveType.PHONE).keepPrefix()).isZero();
    }

    @Test
    void snapshotIsImmutable() {
        assertThatThrownBy(() -> properties.currentRules().put("HACK", MaskRule.of(0, 0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void handedOutRuleIsStable() {
        MaskRule before = properties.ruleOf(SensitiveType.PHONE);

        properties.getRules().getPhone().setKeepPrefix(0);
        properties.getRules().getPhone().setKeepSuffix(0);
        properties.rebuildSnapshot();

        assertThat(before.keepPrefix()).isEqualTo(3);
        assertThat(before.keepSuffix()).isEqualTo(4);
        assertThat(properties.ruleOf(SensitiveType.PHONE).keepPrefix()).isZero();
    }

    @Test
    void extrasAreNormalizedIntoSnapshot() {
        properties.getRules().getExtras().put("express", new RuleConfig(2, 4));
        properties.rebuildSnapshot();

        assertThat(properties.ruleOf("EXPRESS")).isEqualTo(MaskRule.of(2, 4));
    }

    @Test
    void ruleVersionStartsAtOne() {
        assertThat(properties.getRuleVersion()).isEqualTo(1);
        properties.bumpRuleVersion();
        assertThat(properties.getRuleVersion()).isEqualTo(2);
    }
}
