package com.learn.mask.engine;

/**
 * 脱敏动作，用于 Micrometer 标签。
 * {@link #DISABLED} 与 {@link #BYPASS} 分开，才能把「总开关关掉」从「ADMIN 旁路」里拆出来。
 */
public enum MaskAction {
    /** 本次执行了打码。 */
    MASK,
    /** 角色旁路或规则/策略缺失而返回原文。 */
    BYPASS,
    /** 输入已呈脱敏形态，未再次打星。 */
    SKIP_ALREADY_MASKED,
    /** 策略执行抛错。 */
    FAIL,
    /** {@code masking.enabled=false}。 */
    DISABLED
}
