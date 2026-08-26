package com.learn.mask.tutorial.ch04;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 策略表：按编码索引策略。
 * <p>
 * 三个设计要点：
 * <ol>
 *   <li>构造函数收一个 {@code List<MaskStrategy>}。这样一旦接入 Spring，
 *       容器会自动把所有 {@code MaskStrategy} Bean 注入进来——业务加策略只需要 {@code @Component}。</li>
 *   <li>编码统一 trim + 大写，避免 {@code "phone"} 和 {@code "PHONE"} 被当成两个策略。</li>
 *   <li>查不到时**回落到 CUSTOM**，而不是返回 null。调用方永远拿到一个可用策略。</li>
 * </ol>
 */
public class MaskStrategyRegistry {

    private final Map<String, MaskStrategy> strategies = new LinkedHashMap<>();

    public MaskStrategyRegistry(List<MaskStrategy> maskStrategies) {
        for (MaskStrategy strategy : maskStrategies) {
            register(strategy);
        }
    }

    public MaskStrategy get(SensitiveType type) {
        return get(type == null ? null : type.name());
    }

    public MaskStrategy get(String code) {
        MaskStrategy strategy = strategies.get(normalize(code));
        if (strategy != null) {
            return strategy;
        }
        return strategies.get(SensitiveType.CUSTOM.name());
    }

    /** 同编码重复注册时后者覆盖前者，业务可以用这个特性替换内置策略。 */
    public void register(MaskStrategy strategy) {
        if (strategy == null) {
            return;
        }
        strategies.put(normalize(strategy.code()), strategy);
    }

    public static String normalize(String code) {
        if (code == null || code.isBlank()) {
            return SensitiveType.CUSTOM.name();
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    /** 内置策略的标准装配，测试和第 6 章都会用到。 */
    public static MaskStrategyRegistry withBuiltins() {
        return new MaskStrategyRegistry(List.of(
                new PhoneMaskStrategy(),
                new IdCardMaskStrategy(),
                new BankCardMaskStrategy(),
                new EmailMaskStrategy(),
                new CustomPatternMaskStrategy()));
    }
}
