package com.learn.mask.logback;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.slf4j.event.KeyValuePair;

import java.util.List;

/**
 * Logback 转换器，配置 {@code %sensitiveKvp} 后输出 SLF4J fluent API 的键值对，并对 value 做与 {@code %sensitiveMsg} 相同的脱敏。
 * 格式与原生 {@code %kvp} 一致（默认 value 用双引号）；可选 {@code DOUBLE}、{@code SINGLE}、{@code NONE}。
 */
public class SensitiveKeyValuePairConverter extends ClassicConverter {

    /** 与原生 {@code %kvp} 相同的 value 引号策略，由 pattern 第一个选项解析。 */
    private ValueQuoteSpecification valueQuoteSpec = ValueQuoteSpecification.DOUBLE;

    /** 读取 {@code %sensitiveKvp{DOUBLE|SINGLE|NONE}} 的选项并初始化引号规格。 */
    @Override
    public void start() {
        valueQuoteSpec = ValueQuoteSpecification.fromOption(getFirstOption());
        super.start();
    }

    /** 将 event 中的键值对格式化为 {@code key="value"} 串，每个 value 单独过脱敏。 */
    @Override
    public String convert(ILoggingEvent event) {
        List<KeyValuePair> pairs = event.getKeyValuePairs();
        if (pairs == null || pairs.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < pairs.size(); i++) {
            if (i > 0) {
                builder.append(' ');
            }
            KeyValuePair pair = pairs.get(i);
            builder.append(String.valueOf(pair.key)).append('=');
            Character quote = valueQuoteSpec.quoteChar();
            if (quote != null) {
                builder.append(quote);
            }
            builder.append(SensitiveLogTextMasker.mask(String.valueOf(pair.value)));
            if (quote != null) {
                builder.append(quote);
            }
        }
        return builder.toString();
    }

    /** 对齐 Logback {@code KeyValuePairConverter} 的 value 引号选项。 */
    private enum ValueQuoteSpecification {
        DOUBLE('"'),
        SINGLE('\''),
        NONE(null);

        /** {@code null} 表示 value 不加引号。 */
        private final Character quoteChar;

        ValueQuoteSpecification(Character quoteChar) {
            this.quoteChar = quoteChar;
        }

        Character quoteChar() {
            return quoteChar;
        }

        static ValueQuoteSpecification fromOption(String option) {
            if (option == null) {
                return DOUBLE;
            }
            if ("DOUBLE".equalsIgnoreCase(option)) {
                return DOUBLE;
            }
            if ("SINGLE".equalsIgnoreCase(option)) {
                return SINGLE;
            }
            if ("NONE".equalsIgnoreCase(option)) {
                return NONE;
            }
            return DOUBLE;
        }
    }
}
