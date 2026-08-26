package com.learn.mask.tutorial.ch04;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MaskStrategyRegistryTest {

    @Test
    void findsBuiltinStrategiesByType() {
        MaskStrategyRegistry registry = MaskStrategyRegistry.withBuiltins();
        assertThat(registry.get(SensitiveType.PHONE)).isInstanceOf(PhoneMaskStrategy.class);
        assertThat(registry.get(SensitiveType.EMAIL)).isInstanceOf(EmailMaskStrategy.class);
    }

    @Test
    @DisplayName("编码大小写和空格不敏感")
    void codeLookupIsNormalized() {
        MaskStrategyRegistry registry = MaskStrategyRegistry.withBuiltins();
        assertThat(registry.get("phone")).isInstanceOf(PhoneMaskStrategy.class);
        assertThat(registry.get("  Phone  ")).isInstanceOf(PhoneMaskStrategy.class);
    }

    @Test
    @DisplayName("查不到时回落 CUSTOM，而不是返回 null")
    void unknownCodeFallsBackToCustom() {
        MaskStrategyRegistry registry = MaskStrategyRegistry.withBuiltins();
        assertThat(registry.get("PASSPORT")).isInstanceOf(CustomPatternMaskStrategy.class);
        assertThat(registry.get((String) null)).isInstanceOf(CustomPatternMaskStrategy.class);
        assertThat(registry.get("")).isInstanceOf(CustomPatternMaskStrategy.class);
    }

    @Test
    @DisplayName("业务策略注册进来后，按业务编码就能查到")
    void customStrategyIsDiscoverableByCode() {
        MaskStrategyRegistry registry = MaskStrategyRegistry.withBuiltins();
        registry.register(new ExpressNoMaskStrategy());

        assertThat(registry.get(ExpressNoMaskStrategy.EXPRESS)).isInstanceOf(ExpressNoMaskStrategy.class);
        // 内置类型不受影响：自定义策略的 type() 是 CUSTOM，但它占的是 EXPRESS 这个坑位
        assertThat(registry.get(SensitiveType.CUSTOM)).isInstanceOf(CustomPatternMaskStrategy.class);
    }

    @Test
    @DisplayName("同编码后注册者覆盖前者 —— 业务可以借此替换内置策略")
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
}
