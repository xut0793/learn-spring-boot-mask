package com.learn.mask.demo.mask;

import com.learn.mask.annotation.SensitiveType;
import com.learn.mask.config.MaskRule;
import com.learn.mask.strategy.MaskStrategy;
import com.learn.mask.support.MaskUtils;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Demo 自定义快递单号脱敏：保留承运商字母前缀和末尾国家码，数字段按保留前后缀打星。
 * <p>
 * starter 枚举中没有 EXPRESS。本策略通过 {@link #code()} 注册，字段使用
 * {@code @Sensitive(code = DemoSensitiveTypes.EXPRESS)}。
 */
@Component
public class ExpressNoMaskStrategy implements MaskStrategy {

    private static final Pattern EXPRESS = Pattern.compile("^([A-Za-z]*)(\\d+)([A-Za-z]*)$");

    @Override
    public SensitiveType type() {
        return SensitiveType.CUSTOM;
    }

    @Override
    public String code() {
        return DemoSensitiveTypes.EXPRESS;
    }

    @Override
    public String mask(String raw, MaskRule rule) {
        if (MaskUtils.isBlank(raw) || rule == null || !rule.isEnabled()) {
            return raw;
        }
        String trimmed = raw.strip();
        Matcher matcher = EXPRESS.matcher(trimmed);
        if (matcher.matches()) {
            return matcher.group(1) + MaskUtils.keepMask(matcher.group(2), rule) + matcher.group(3);
        }
        return MaskUtils.keepMask(trimmed, rule);
    }

    @Override
    public boolean alreadyMasked(String raw, MaskRule rule) {
        if (MaskUtils.isBlank(raw) || rule == null) {
            return false;
        }
        String trimmed = raw.strip();
        if (trimmed.indexOf(rule.getMaskChar()) >= 0) {
            return true;
        }
        Matcher matcher = EXPRESS.matcher(trimmed);
        if (matcher.matches()) {
            return MaskUtils.alreadyKeepMasked(matcher.group(2), rule);
        }
        return MaskUtils.alreadyKeepMasked(trimmed, rule);
    }
}
