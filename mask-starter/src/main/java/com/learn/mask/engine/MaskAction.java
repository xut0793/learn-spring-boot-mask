package com.learn.mask.engine;

/**
 * 脱敏动作，用于 Micrometer 标签。
 * {@link #DISABLED} 与 {@link #BYPASS} 分开，才能把「总开关关掉」从「ADMIN 旁路」里拆出来。
 */
public enum MaskAction {
    MASK,
    BYPASS,
    SKIP_ALREADY_MASKED,
    FAIL,
    DISABLED
}
