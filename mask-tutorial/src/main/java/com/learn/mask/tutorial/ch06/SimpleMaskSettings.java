package com.learn.mask.tutorial.ch06;

import com.learn.mask.tutorial.ch04.MaskRule;
import com.learn.mask.tutorial.ch04.MaskStrategyRegistry;
import com.learn.mask.tutorial.ch04.SensitiveType;

import java.util.HashMap;
import java.util.Map;

/**
 * 内存版配置，本章测试用。第 7 章会有一个从 YAML 绑定并支持热更新的实现。
 * <p>
 * 提供了一份和 {@code mask-demo} 一致的默认规则，这样本章的测试期望值
 * 和第 3 章实验里看到的输出能对上。
 */
public class SimpleMaskSettings implements MaskSettings {

    private boolean enabled = true;
    private final Map<String, MaskRule> rules = new HashMap<>();

    public static SimpleMaskSettings withDemoDefaults() {
        SimpleMaskSettings settings = new SimpleMaskSettings();
        settings.put(SensitiveType.PHONE, MaskRule.of(3, 4));
        settings.put(SensitiveType.ID_CARD, MaskRule.of(6, 4));
        settings.put(SensitiveType.BANK_CARD, MaskRule.of(4, 4));
        settings.put(SensitiveType.EMAIL, MaskRule.of(1, 0));
        settings.put(SensitiveType.CUSTOM, MaskRule.of(1, 1));
        return settings;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public SimpleMaskSettings setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    @Override
    public MaskRule ruleOf(String code) {
        return rules.get(MaskStrategyRegistry.normalize(code));
    }

    public SimpleMaskSettings put(SensitiveType type, MaskRule rule) {
        return put(type.name(), rule);
    }

    public SimpleMaskSettings put(String code, MaskRule rule) {
        rules.put(MaskStrategyRegistry.normalize(code), rule);
        return this;
    }

    public SimpleMaskSettings remove(String code) {
        rules.remove(MaskStrategyRegistry.normalize(code));
        return this;
    }
}
