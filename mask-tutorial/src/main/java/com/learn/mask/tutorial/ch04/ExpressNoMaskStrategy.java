package com.learn.mask.tutorial.ch04;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 4.8 节的扩展实战：快递单号脱敏。
 * <p>
 * 需求：{@code SF1234567890123} 要保留承运商前缀 {@code SF} 和末尾 4 位，中间数字打星；
 * 国际单号 {@code EA123456789CN} 末尾的国家代码也要保留。
 * <p>
 * 重点在于**为了支持这个需求，前面所有的类都不需要改一行**：
 * <ul>
 *   <li>不改 {@link SensitiveType} 枚举 —— 用 {@link #code()} 返回字符串编码</li>
 *   <li>不改 {@link MaskStrategyRegistry} —— 它按编码索引，不认识具体类型</li>
 *   <li>不改 {@link MaskUtils} —— 数字段的打星复用 {@code keepMask}</li>
 * </ul>
 * 接入 Spring 后，这个类只要标上 {@code @Component} 就会被自动收集进策略表。
 */
public class ExpressNoMaskStrategy implements MaskStrategy {

    /** 业务自定义编码。放成常量，字段注解和配置里都引用它，避免手写字符串写错。 */
    public static final String EXPRESS = "EXPRESS";

    /** 前缀字母（可空）+ 数字段 + 后缀字母（可空）。 */
    private static final Pattern EXPRESS_PATTERN = Pattern.compile("^([A-Za-z]*)(\\d+)([A-Za-z]*)$");

    @Override
    public SensitiveType type() {
        return SensitiveType.CUSTOM;
    }

    @Override
    public String code() {
        return EXPRESS;
    }

    @Override
    public String mask(String raw, MaskRule rule) {
        if (MaskUtils.isBlank(raw) || rule == null || !rule.enabled()) {
            return raw;
        }
        String trimmed = raw.strip();
        Matcher matcher = EXPRESS_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            return matcher.group(1) + MaskUtils.keepMask(matcher.group(2), rule) + matcher.group(3);
        }
        // 格式不认识就退回通用打星，绝不原样返回明文。
        return MaskUtils.keepMask(trimmed, rule);
    }

    @Override
    public boolean alreadyMasked(String raw, MaskRule rule) {
        if (MaskUtils.isBlank(raw) || rule == null) {
            return false;
        }
        String trimmed = raw.strip();
        // 快递单号只由字母和数字组成，正常明文里不可能出现掩码字符。
        // 所以「含掩码字符」就足以判定已打码，比逐位比对更简单也更稳。
        // 代价是：如果某种单号格式合法地包含 '*'，就会被误判为已打码而跳过脱敏。
        // 用这个捷径的前提是你确认自己的数据格式里不会出现掩码字符。
        if (trimmed.indexOf(rule.maskChar()) >= 0) {
            return true;
        }
        Matcher matcher = EXPRESS_PATTERN.matcher(trimmed);
        if (matcher.matches()) {
            return MaskUtils.alreadyKeepMasked(matcher.group(2), rule);
        }
        return MaskUtils.alreadyKeepMasked(trimmed, rule);
    }
}
