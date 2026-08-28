package com.learn.mask.strategy;

import com.learn.mask.annotation.SensitiveType;
import com.learn.mask.testsupport.MaskingFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MaskStrategyRegistryTest {

    @Test
    @DisplayName("按内置类型能查到对应策略")
    void findsBuiltinStrategiesByType() {
        MaskStrategyRegistry registry = MaskingFixtures.builtinRegistry();
        assertThat(registry.get(SensitiveType.PHONE)).isInstanceOf(PhoneMaskStrategy.class);
        assertThat(registry.get(SensitiveType.EMAIL)).isInstanceOf(EmailMaskStrategy.class);
    }

    @Test
    @DisplayName("编码查找会 trim + 大写")
    void codeLookupIsNormalized() {
        MaskStrategyRegistry registry = MaskingFixtures.builtinRegistry();
        assertThat(registry.get("phone")).isInstanceOf(PhoneMaskStrategy.class);
        assertThat(registry.get("  Phone  ")).isInstanceOf(PhoneMaskStrategy.class);
    }

    @Test
    @DisplayName("查不到时回落 CUSTOM，而不是返回 null")
    void unknownCodeFallsBackToCustom() {
        MaskStrategyRegistry registry = MaskingFixtures.builtinRegistry();
        assertThat(registry.get("PASSPORT")).isInstanceOf(CustomPatternMaskStrategy.class);
        assertThat(registry.get((String) null)).isInstanceOf(CustomPatternMaskStrategy.class);
        assertThat(registry.get("")).isInstanceOf(CustomPatternMaskStrategy.class);
    }

    @Test
    @DisplayName("业务策略按自己的 code() 占坑，不覆盖内置 CUSTOM")
    void customStrategyIsDiscoverableByCode() {
        MaskStrategy express = new AbstractKeepMaskStrategy() {
            @Override
            public SensitiveType type() {
                return SensitiveType.CUSTOM;
            }

            @Override
            public String code() {
                return "EXPRESS";
            }
        };
        MaskStrategyRegistry registry = MaskingFixtures.builtinRegistry();
        registry.register(express);

        assertThat(registry.get("EXPRESS")).isSameAs(express);
        assertThat(registry.get(SensitiveType.CUSTOM)).isInstanceOf(CustomPatternMaskStrategy.class);
    }

    @Test
    @DisplayName("同编码后注册者覆盖前者，业务可以替换内置策略")
    void laterRegistrationWins() {
        MaskStrategy stricterPhone = new AbstractKeepMaskStrategy() {
            @Override
            public SensitiveType type() {
                return SensitiveType.PHONE;
            }
        };
        MaskStrategyRegistry registry = new MaskStrategyRegistry(
                List.of(new PhoneMaskStrategy(), stricterPhone));

        assertThat(registry.get(SensitiveType.PHONE)).isSameAs(stricterPhone);
    }

    @Test
    @DisplayName("resolve：code 非空优先于枚举名")
    void resolvePrefersCode() {
        assertThat(MaskStrategyRegistry.resolve("express", SensitiveType.PHONE)).isEqualTo("EXPRESS");
        assertThat(MaskStrategyRegistry.resolve("  ", SensitiveType.EMAIL)).isEqualTo("EMAIL");
        assertThat(MaskStrategyRegistry.resolve(null, null)).isEqualTo("CUSTOM");
    }
}
