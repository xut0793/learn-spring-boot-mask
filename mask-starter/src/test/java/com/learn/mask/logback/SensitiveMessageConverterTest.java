package com.learn.mask.logback;

import ch.qos.logback.classic.spi.LoggingEvent;
import com.learn.mask.cache.MaskCache;
import com.learn.mask.config.MaskingProperties;
import com.learn.mask.context.MaskContext;
import com.learn.mask.engine.AlreadyMaskedDetector;
import com.learn.mask.engine.MaskEngine;
import com.learn.mask.metrics.MaskingMetrics;
import com.learn.mask.strategy.BankCardMaskStrategy;
import com.learn.mask.strategy.CustomPatternMaskStrategy;
import com.learn.mask.strategy.EmailMaskStrategy;
import com.learn.mask.strategy.IdCardMaskStrategy;
import com.learn.mask.strategy.MaskStrategyRegistry;
import com.learn.mask.strategy.PhoneMaskStrategy;
import com.learn.mask.support.MaskingSpringBridge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveMessageConverterTest {

    @AfterEach
    void tearDown() {
        MaskingSpringBridge.unbind();
        MaskContext.clearHeaderRole();
    }

    @Test
    void masksPhoneInLogMessage() {
        MaskingProperties properties = new MaskingProperties();
        MaskEngine engine = new MaskEngine(
                properties,
                new MaskStrategyRegistry(List.of(
                        new PhoneMaskStrategy(),
                        new IdCardMaskStrategy(),
                        new BankCardMaskStrategy(),
                        new EmailMaskStrategy(),
                        new CustomPatternMaskStrategy()
                )),
                new MaskCache(properties),
                new AlreadyMaskedDetector(),
                new MaskingMetrics(new SimpleMeterRegistry())
        );
        MaskingSpringBridge.bind(engine, properties, new MaskContext(properties));

        LoggingEvent event = new LoggingEvent();
        event.setMessage("loaded user phone=13812345678 email=zhangsan@example.com");
        String converted = new SensitiveMessageConverter().convert(event);

        assertThat(converted).doesNotContain("13812345678");
        assertThat(converted).doesNotContain("zhangsan@example.com");
        assertThat(converted).contains("138****5678");
        assertThat(converted).contains("z*******@example.com");
    }

    @Test
    void idCardAndEmailAreMaskedBeforeBankCardPattern() {
        MaskingProperties properties = new MaskingProperties();
        MaskEngine engine = new MaskEngine(
                properties,
                new MaskStrategyRegistry(List.of(
                        new PhoneMaskStrategy(),
                        new IdCardMaskStrategy(),
                        new BankCardMaskStrategy(),
                        new EmailMaskStrategy(),
                        new CustomPatternMaskStrategy()
                )),
                new MaskCache(properties),
                new AlreadyMaskedDetector(),
                new MaskingMetrics(new SimpleMeterRegistry())
        );
        MaskingSpringBridge.bind(engine, properties, new MaskContext(properties));

        LoggingEvent event = new LoggingEvent();
        event.setMessage("id=110101199003078515 card=6222021234567890123");
        String converted = new SensitiveMessageConverter().convert(event);

        assertThat(converted).contains("110101********8515");
        assertThat(converted).contains("6222***********0123");
        assertThat(converted).doesNotContain("110101199003078515");
    }

    @Test
    void unboundStillRedactsPlaintext() {
        LoggingEvent event = new LoggingEvent();
        event.setMessage("loaded user phone=13812345678 email=zhangsan@example.com");
        String converted = new SensitiveMessageConverter().convert(event);

        assertThat(converted).doesNotContain("13812345678");
        assertThat(converted).doesNotContain("zhangsan@example.com");
        assertThat(converted).contains("***********");
    }
}
