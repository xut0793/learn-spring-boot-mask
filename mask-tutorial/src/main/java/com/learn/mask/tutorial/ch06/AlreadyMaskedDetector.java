package com.learn.mask.tutorial.ch06;

import com.learn.mask.tutorial.ch04.MaskRule;
import com.learn.mask.tutorial.ch04.MaskStrategy;

/**
 * 幂等判定的入口。它自己不做判断，只统一处理空值后委托给策略。
 * <p>
 * 你可能会问：既然只有三行，为什么不直接在引擎里调
 * {@code strategy.alreadyMasked(raw, rule)}？
 * <p>
 * 两个理由：
 * <ol>
 *   <li>空值守卫需要一个归属地。引擎里已经有八步判定了，再塞三行 null 检查会更乱。</li>
 *   <li>**它是一个扩展点。** 幂等判定的策略可能需要全局调整——比如某些团队想加一条
 *       「值里含掩码字符就算已脱敏」的全局兜底（第 4 章练习 4.3 讨论过它的风险），
 *       或者想在判定为「已脱敏」时打一条 debug 日志排查通道叠加。
 *       有这个类在，改一个地方就够了。</li>
 * </ol>
 * 不过要承认：这是一个**预留的**扩展点，当前没有第二个实现。
 * 这类抽象值不值得，取决于你对「将来会不会需要」的判断，见第 6 章 6.9 节。
 */
public final class AlreadyMaskedDetector {

    public boolean isAlreadyMasked(String raw, MaskStrategy strategy, MaskRule rule) {
        if (raw == null || strategy == null || rule == null) {
            return false;
        }
        return strategy.alreadyMasked(raw, rule);
    }
}
