package com.learn.mask.config;

/**
 * 一条规则的可变配置载体，承接 YAML 绑定和热更新写入。
 * <p>
 * Spring Boot 宽松绑定需要无参构造 + setter，才能在只写了 {@code keep-prefix: 3}
 * 时保留其余默认值。{@link MaskRule} 是给引擎读的不可变快照。
 */
public class RuleConfig {

    /** 为 false 时引擎对该类型旁路。 */
    private boolean enabled = true;
    /** 保留明文前缀长度。 */
    private int keepPrefix = 1;
    /** 保留明文后缀长度。 */
    private int keepSuffix = 1;
    private char maskChar = '*';

    /** Spring Boot 宽松绑定用。 */
    public RuleConfig() {
    }

    /** 内置类型在 {@link MaskingProperties.RuleSet} 中的默认前后缀。 */
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
