package com.learn.mask.tutorial.ch07;

/**
 * 反面教材：一个被多个线程共享、且会被热更新**原地修改**的规则对象。
 * <p>
 * {@code mask-starter} 的 {@code MaskRule} 就是这个形态：{@code MaskingReloadController}
 * 拿到规则对象后连续调用四个 setter，而同一时刻可能有请求正在读它。
 * <p>
 * 问题不是「读到旧值」——那无所谓，下一个请求就对了。问题是**读到一个从未配置过的组合**：
 * 改完 {@code keepPrefix} 还没改 {@code keepSuffix} 的那一刻，规则是「新前缀 + 旧后缀」。
 * <p>
 * {@code InPlaceMutableRuleTest} 会算出这个中间态到底有多危险。
 * <p>
 * 这个类只用于教学对比，不要在任何地方使用。
 */
public class InPlaceMutableRule {

    private int keepPrefix;
    private int keepSuffix;

    public InPlaceMutableRule(int keepPrefix, int keepSuffix) {
        this.keepPrefix = keepPrefix;
        this.keepSuffix = keepSuffix;
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

    /** 按当前字段打星，等价于 {@code MaskUtils.keepMask}。 */
    public String mask(String raw) {
        int len = raw.length();
        if (len <= keepPrefix + keepSuffix) {
            return "*".repeat(len);
        }
        return raw.substring(0, keepPrefix)
                + "*".repeat(len - keepPrefix - keepSuffix)
                + raw.substring(len - keepSuffix);
    }
}
