package com.learn.mask.config;

/**
 * 单类型脱敏规则：是否启用、保留前后缀长度、掩码字符。
 */
public class MaskRule {

    private boolean enabled = true;
    private int keepPrefix = 1;
    private int keepSuffix = 1;
    private char maskChar = '*';

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
