package com.learn.mask.logback;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Logback 转换器，配置 {@code %sensitiveMsg} 后按正则扫描日志中的手机号、证件号、邮箱、卡号并脱敏。
 * 无法注入 Spring，通过 {@link com.learn.mask.support.MaskingSpringBridge} 取引擎。
 * 引擎未 bind 时仍用星号盖住匹配段，避免启动窗口写明文。
 */
public class SensitiveMessageConverter extends ClassicConverter {

    /** 输出 {@link ILoggingEvent#getFormattedMessage()} 经 {@link SensitiveLogTextMasker} 处理后的正文。 */
    @Override
    public String convert(ILoggingEvent event) {
        return SensitiveLogTextMasker.mask(event.getFormattedMessage());
    }
}
