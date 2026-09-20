package com.learn.mask.engine;

import com.learn.mask.config.MaskRule;
import com.learn.mask.strategy.MaskStrategy;

/**
 * 委托策略判断字符串是否已是脱敏形态，避免多通道叠加时二次打星。
 */
public final class AlreadyMaskedDetector {

    /** 委托策略判断，避免 Jackson + AOP 等对同一字段重复打星。 */
    public boolean isAlreadyMasked(String raw, MaskStrategy strategy, MaskRule rule) {
        if (raw == null || strategy == null || rule == null) {
            return false;
        }
        return strategy.alreadyMasked(raw, rule);
    }
}
