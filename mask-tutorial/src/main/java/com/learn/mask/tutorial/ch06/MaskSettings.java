package com.learn.mask.tutorial.ch06;

import com.learn.mask.tutorial.ch04.MaskRule;

/**
 * 引擎需要从配置里知道的**全部**信息，只有两件事。
 * <p>
 * 刻意定义成一个窄接口而不是让引擎直接依赖具体配置类，收益在后面两章体现：
 * <ul>
 *   <li>第 7 章接入 {@code @ConfigurationProperties} 和热更新时，
 *       只需要写一个新的实现，{@link MaskEngine} 一行都不用改</li>
 *   <li>本章的测试可以用 {@link SimpleMaskSettings} 直接构造规则，
 *       不需要启动 Spring，也不需要 YAML 文件</li>
 * </ul>
 * 这就是依赖倒置：引擎定义自己需要什么，而不是去适配别人提供什么。
 */
public interface MaskSettings {

    /** 脱敏总开关。关闭时引擎对所有输入直接旁路。 */
    boolean isEnabled();

    /**
     * 查某个类型编码对应的规则。
     * <p>
     * 返回 {@code null} 表示「没有为这个类型配规则」，引擎会旁路。
     */
    MaskRule ruleOf(String code);
}
