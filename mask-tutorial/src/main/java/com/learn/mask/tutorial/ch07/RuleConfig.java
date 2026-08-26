package com.learn.mask.tutorial.ch07;

import com.learn.mask.tutorial.ch04.MaskRule;

/**
 * 一条规则的**可变**配置载体。
 * <p>
 * 为什么不直接用第 4 章那个不可变的 {@link MaskRule}？因为 Spring Boot 的
 * 宽松绑定（relaxed binding）需要无参构造 + setter：它要能在只写了
 * {@code keep-prefix: 3} 的情况下构造对象，其余字段保留默认值。
 * record 的构造器要求一次性给全所有参数，做不到「部分覆盖」。
 * <p>
 * 所以分工是：
 * <ul>
 *   <li>{@code RuleConfig}：可变，只负责承接 YAML 和热更新的写入</li>
 *   <li>{@link MaskRule}：不可变，是给引擎读的快照</li>
 * </ul>
 * 详见第 7 章 7.3 节。
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
