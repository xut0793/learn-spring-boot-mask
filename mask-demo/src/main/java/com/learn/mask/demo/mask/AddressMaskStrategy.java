package com.learn.mask.demo.mask;

import com.learn.mask.annotation.SensitiveType;
import com.learn.mask.config.MaskRule;
import com.learn.mask.strategy.MaskStrategy;
import com.learn.mask.support.MaskUtils;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Demo 自定义地址脱敏：保留道路/小区名称，将末尾门牌号替换为掩码。
 * <p>
 * 接入方式：实现 {@link MaskStrategy} 并注册为 Spring Bean，starter 会按 {@link #type()} 自动纳入策略表。
 * 示例：{@code Chaoyang Road 88} → {@code Chaoyang Road **}；{@code 朝阳路88号} → {@code 朝阳路**号}。
 * 没有门牌号时回落到 {@code masking.rules.address} 的保留前后缀规则。
 */
@Component
public class AddressMaskStrategy implements MaskStrategy {

    private static final Pattern HOUSE_NUMBER = Pattern.compile("^(.*?)(\\d+)(号)?$");
    private static final Pattern ALREADY_MASKED = Pattern.compile("^.+\\*+(号)?$");

    @Override
    public SensitiveType type() {
        return SensitiveType.ADDRESS;
    }

    @Override
    public String mask(String raw, MaskRule rule) {
        if (MaskUtils.isBlank(raw) || rule == null || !rule.isEnabled()) {
            return raw;
        }
        String trimmed = raw.strip();
        Matcher matcher = HOUSE_NUMBER.matcher(trimmed);
        if (matcher.matches()) {
            String street = matcher.group(1);
            String unit = matcher.group(3) == null ? "" : matcher.group(3);
            return street + String.valueOf(rule.getMaskChar()).repeat(2) + unit;
        }
        return MaskUtils.keepMask(trimmed, rule);
    }

    @Override
    public boolean alreadyMasked(String raw, MaskRule rule) {
        if (MaskUtils.isBlank(raw)) {
            return false;
        }
        return ALREADY_MASKED.matcher(raw.strip()).matches()
                || MaskUtils.alreadyKeepMasked(raw, rule);
    }
}
