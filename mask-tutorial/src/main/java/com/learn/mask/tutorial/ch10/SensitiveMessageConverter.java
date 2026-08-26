package com.learn.mask.tutorial.ch10;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.learn.mask.tutorial.ch04.SensitiveType;
import com.learn.mask.tutorial.ch06.MaskEngine;
import com.learn.mask.tutorial.ch07.MaskingProperties;
import com.learn.mask.tutorial.ch09.ChannelProperties;
import com.learn.mask.tutorial.ch09.MaskingSpringBridge;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Logback 转换器。pattern 里写 {@code %sensitiveMsg} 代替 {@code %msg}，
 * 日志正文会按正则扫一遍手机号 / 身份证 / 邮箱 / 银行卡并交给引擎。
 * <p>
 * 和 Jackson 通道一样拿不到 Spring 注入，引擎从 {@link MaskingSpringBridge} 取。
 * <p>
 * <b>替换顺序不能改：</b>{@code ID_CARD} 和 {@code EMAIL} 必须在 {@code BANK_CARD} 之前。
 * 银行卡正则是「16~19 位数字」，会把身份证号、邮箱本地段里的长数字一起吃掉。
 */
public class SensitiveMessageConverter extends ClassicConverter {

    /** 中国手机号：1 开头的 11 位，左右不能再是数字，避免从更长数字里切出一段。 */
    private static final Pattern PHONE = Pattern.compile("(?<![0-9])(1[3-9][0-9]{9})(?![0-9])");
    /** 18 位身份证，最后一位可以是 X。 */
    private static final Pattern ID_CARD = Pattern.compile("(?<![0-9])([0-9]{17}[0-9Xx])(?![0-9])");
    private static final Pattern EMAIL = Pattern.compile("([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");
    /** 16~19 位数字。范围覆盖了身份证的 18 位，所以必须放在 ID_CARD 和 EMAIL 后面。 */
    private static final Pattern BANK_CARD = Pattern.compile("(?<![0-9])([0-9]{16,19})(?![0-9])");

    @Override
    public String convert(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        MaskingProperties properties = MaskingSpringBridge.properties();
        MaskEngine engine = MaskingSpringBridge.engine();
        ChannelProperties channels = MaskingSpringBridge.channels();
        if (message == null || properties == null || engine == null
                || !properties.isEnabled()
                || (channels != null && !channels.isLogback())) {
            return message;
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
}
