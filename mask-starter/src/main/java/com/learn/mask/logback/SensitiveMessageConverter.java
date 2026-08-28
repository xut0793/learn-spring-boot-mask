package com.learn.mask.logback;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.learn.mask.annotation.SensitiveType;
import com.learn.mask.config.MaskingProperties;
import com.learn.mask.engine.MaskEngine;
import com.learn.mask.support.MaskingSpringBridge;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logback 转换器，配置 {@code %sensitiveMsg} 后按正则扫描日志中的手机号、证件号、邮箱、卡号并脱敏。
 * 无法注入 Spring，通过 {@link MaskingSpringBridge} 取引擎。
 * 引擎未 bind 时仍用星号盖住匹配段，避免启动窗口写明文。
 */
public class SensitiveMessageConverter extends ClassicConverter {

    private static final Pattern PHONE = Pattern.compile("(?<![0-9])(1[3-9][0-9]{9})(?![0-9])");
    private static final Pattern ID_CARD = Pattern.compile("(?<![0-9])([0-9]{17}[0-9Xx])(?![0-9])");
    private static final Pattern BANK_CARD = Pattern.compile("(?<![0-9])([0-9]{16,19})(?![0-9])");
    private static final Pattern EMAIL = Pattern.compile("([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");

    @Override
    public String convert(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        if (message == null) {
            return null;
        }
        MaskingProperties properties = MaskingSpringBridge.properties();
        MaskEngine engine = MaskingSpringBridge.engine();
        if (properties != null && (!properties.isEnabled() || !properties.getChannels().isLogback())) {
            return message;
        }
        if (engine == null || properties == null) {
            return redactUnbound(message);
        }
        String masked = replace(message, PHONE, SensitiveType.PHONE, engine);
        masked = replace(masked, ID_CARD, SensitiveType.ID_CARD, engine);
        masked = replace(masked, EMAIL, SensitiveType.EMAIL, engine);
        return replace(masked, BANK_CARD, SensitiveType.BANK_CARD, engine);
    }

    private String replace(String source, Pattern pattern, SensitiveType type, MaskEngine engine) {
        Matcher matcher = pattern.matcher(source);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(builder, Matcher.quoteReplacement(
                    engine.apply(matcher.group(1), type, MaskingSpringBridge.context())));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private static String redactUnbound(String source) {
        String masked = starMatch(source, PHONE);
        masked = starMatch(masked, ID_CARD);
        masked = starMatch(masked, EMAIL);
        return starMatch(masked, BANK_CARD);
    }

    private static String starMatch(String source, Pattern pattern) {
        Matcher matcher = pattern.matcher(source);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(builder, Matcher.quoteReplacement("*".repeat(matcher.group().length())));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }
}
