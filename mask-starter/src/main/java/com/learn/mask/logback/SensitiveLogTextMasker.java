package com.learn.mask.logback;

import com.learn.mask.annotation.SensitiveType;
import com.learn.mask.config.MaskingProperties;
import com.learn.mask.engine.MaskEngine;
import com.learn.mask.support.MaskingSpringBridge;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logback 正文与 KVP 值共用的正则扫描脱敏；通道关或未 bind 时的行为与 {@link SensitiveMessageConverter} 一致。
 */
final class SensitiveLogTextMasker {

    /** 中国手机号：左右不能再是数字，避免从更长串里切出一段。 */
    private static final Pattern PHONE = Pattern.compile("(?<![0-9])(1[3-9][0-9]{9})(?![0-9])");
    /** 18 位身份证，末位可为 X。 */
    private static final Pattern ID_CARD = Pattern.compile("(?<![0-9])([0-9]{17}[0-9Xx])(?![0-9])");
    /** 16~19 位数字；须在 ID_CARD、EMAIL 之后替换，否则会误伤身份证与邮箱本地段。 */
    private static final Pattern BANK_CARD = Pattern.compile("(?<![0-9])([0-9]{16,19})(?![0-9])");
    private static final Pattern EMAIL = Pattern.compile("([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");

    private SensitiveLogTextMasker() {
    }

    /**
     * 对一段文本按四类正则扫描并脱敏；通道关闭时原样返回，引擎未 bind 时用等长星号兜底。
     */
    static String mask(String text) {
        if (text == null) {
            return null;
        }
        MaskingProperties properties = MaskingSpringBridge.properties();
        MaskEngine engine = MaskingSpringBridge.engine();
        if (properties != null && (!properties.isEnabled() || !properties.getChannels().isLogback())) {
            return text;
        }
        if (engine == null || properties == null) {
            return redactUnbound(text);
        }
        String masked = replace(text, PHONE, SensitiveType.PHONE, engine);
        masked = replace(masked, ID_CARD, SensitiveType.ID_CARD, engine);
        masked = replace(masked, EMAIL, SensitiveType.EMAIL, engine);
        return replace(masked, BANK_CARD, SensitiveType.BANK_CARD, engine);
    }

    /** 对单个正则捕获组调用引擎，并用 {@link Matcher#quoteReplacement} 安全替换。 */
    private static String replace(String source, Pattern pattern, SensitiveType type, MaskEngine engine) {
        Matcher matcher = pattern.matcher(source);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(builder, Matcher.quoteReplacement(
                    engine.apply(matcher.group(1), type, MaskingSpringBridge.context())));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    /** 引擎尚未 bind 时用等长 {@code *} 盖住匹配段。 */
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
