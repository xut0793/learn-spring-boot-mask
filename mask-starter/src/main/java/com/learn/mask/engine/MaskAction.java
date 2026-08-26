package com.learn.mask.engine;

/**
 * 脱敏动作，用于 Micrometer 标签：实际脱敏、角色旁路、幂等跳过、失败。
 */
public enum MaskAction {
    MASK,
    BYPASS,
    SKIP_ALREADY_MASKED,
    FAIL
}
