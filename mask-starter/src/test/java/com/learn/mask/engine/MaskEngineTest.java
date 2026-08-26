package com.learn.mask.engine;

import com.learn.mask.annotation.SensitiveType;
import com.learn.mask.cache.MaskCache;
import com.learn.mask.config.MaskingProperties;
import com.learn.mask.context.MaskContext;
import com.learn.mask.context.MaskRole;
import com.learn.mask.metrics.MaskingMetrics;
import com.learn.mask.strategy.BankCardMaskStrategy;
import com.learn.mask.strategy.CustomPatternMaskStrategy;
import com.learn.mask.strategy.EmailMaskStrategy;
import com.learn.mask.strategy.IdCardMaskStrategy;
import com.learn.mask.strategy.MaskStrategyRegistry;
import com.learn.mask.strategy.PhoneMaskStrategy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MaskEngineTest {

    private MaskEngine engine;
    private MaskingProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MaskingProperties();
        engine = new MaskEngine(
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
    }

    @AfterEach
    void tearDown() {
        MaskContext.clearHeaderRole();
    }

    @Test
    void masksIdCardForUser() {
        String masked = engine.apply("110101199003078515", SensitiveType.ID_CARD, contextWith(MaskRole.USER));
        assertThat(masked).isEqualTo("110101********8515");
        assertThat(properties.ruleOf(SensitiveType.ID_CARD).getKeepPrefix()).isEqualTo(6);
    }

    @Test
    void masksPhoneForUser() {
        String masked = engine.apply("13812345678", SensitiveType.PHONE, contextWith(MaskRole.USER));
        assertThat(masked).isEqualTo("138****5678");
    }

    @Test
    void bypassesForAdmin() {
        String raw = engine.apply("13812345678", SensitiveType.PHONE, contextWith(MaskRole.ADMIN));
        assertThat(raw).isEqualTo("13812345678");
    }

    @Test
    void skipsAlreadyMasked() {
        String masked = engine.apply("138****5678", SensitiveType.PHONE, contextWith(MaskRole.USER));
        assertThat(masked).isEqualTo("138****5678");
    }

    @Test
    void disabledReturnsRaw() {
        properties.setEnabled(false);
        assertThat(engine.apply("13812345678", SensitiveType.PHONE, contextWith(MaskRole.USER)))
                .isEqualTo("13812345678");
    }

    private MaskContext contextWith(MaskRole role) {
        properties.getDebug().setHeaderRoleEnabled(true);
        MaskContext.setHeaderRole(role);
        return new MaskContext(properties);
    }
}
