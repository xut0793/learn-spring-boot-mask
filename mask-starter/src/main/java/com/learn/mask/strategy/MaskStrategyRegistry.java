package com.learn.mask.strategy;

import com.learn.mask.annotation.SensitiveType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 按策略编码索引。同编码后注册者覆盖先注册者；未命中时回落到 {@link SensitiveType#CUSTOM}。
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

    public void register(MaskStrategy strategy) {
        if (strategy == null) {
            return;
        }
        strategies.put(normalize(strategy.code()), strategy);
    }

    private static String normalize(String code) {
        if (code == null || code.isBlank()) {
            return SensitiveType.CUSTOM.name();
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
