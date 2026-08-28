package com.learn.mask.config;

/**
 * 一条规则的可变配置载体，承接 YAML 绑定和热更新写入。
 * <p>
 * Spring Boot 宽松绑定需要无参构造 + setter，才能在只写了 {@code keep-prefix: 3}
 * 时保留其余默认值。{@link MaskRule} 是给引擎读的不可变快照。
 */
public class RuleConfig {

    private boolean enabled = true;
    private int keepPrefix = 1;
    private int keepSuffix = 1;
    private char maskChar = '*';

    public RuleConfig() {
    }

    public RuleConfig(int keepPrefix, int keepSuffix) {
        this.keepPrefix = keepPrefix;
        this.keepSuffix = keepSuffix;
    }

    /** 生成不可变快照。每次调用返回新对象，调用方拿到的规则不会再变。 */
    public MaskRule toRule() {
        return new MaskRule(enabled, keepPrefix, keepSuffix, maskChar);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getKeepPrefix() {
        return keepPrefix;
    }

    public void setKeepPrefix(int keepPrefix) {
        this.keepPrefix = keepPrefix;
    }

    public int getKeepSuffix() {
        return keepSuffix;
    }

    public void setKeepSuffix(int keepSuffix) {
        this.keepSuffix = keepSuffix;
    }

    public char getMaskChar() {
        return maskChar;
    }

    public void setMaskChar(char maskChar) {
        this.maskChar = maskChar;
    }
}
