package com.learn.mask.engine;

import com.learn.mask.annotation.SensitiveType;
import com.learn.mask.cache.MaskResultCache;
import com.learn.mask.config.MaskingProperties;
import com.learn.mask.context.MaskContext;
import com.learn.mask.context.MaskRole;
import com.learn.mask.metrics.MaskRecorder;
import com.learn.mask.strategy.BankCardMaskStrategy;
import com.learn.mask.strategy.CustomPatternMaskStrategy;
import com.learn.mask.strategy.EmailMaskStrategy;
import com.learn.mask.strategy.IdCardMaskStrategy;
import com.learn.mask.strategy.MaskStrategyRegistry;
import com.learn.mask.strategy.PhoneMaskStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class MaskEngineTest {

    private MaskEngine engine;
    private MaskingProperties properties;
    private CountingCache cache;
    private RecordingRecorder recorder;

    @BeforeEach
    void setUp() {
        properties = new MaskingProperties();
        cache = new CountingCache();
        recorder = new RecordingRecorder();
        engine = new MaskEngine(
                properties,
                new MaskStrategyRegistry(List.of(
                        new PhoneMaskStrategy(),
                        new IdCardMaskStrategy(),
                        new BankCardMaskStrategy(),
                        new EmailMaskStrategy(),
                        new CustomPatternMaskStrategy()
                )),
                cache,
                new AlreadyMaskedDetector(),
                recorder
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
        assertThat(properties.ruleOf(SensitiveType.ID_CARD).keepPrefix()).isEqualTo(6);
        assertThat(recorder.lastAction()).isEqualTo(MaskAction.MASK);
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
        assertThat(recorder.lastAction()).isEqualTo(MaskAction.BYPASS);
    }

    @Test
    void skipsAlreadyMaskedWithoutTouchingCache() {
        String masked = engine.apply("138****5678", SensitiveType.PHONE, contextWith(MaskRole.USER));
        assertThat(masked).isEqualTo("138****5678");
        assertThat(recorder.lastAction()).isEqualTo(MaskAction.SKIP_ALREADY_MASKED);
        assertThat(cache.getCount.get()).isZero();
        assertThat(cache.putCount.get()).isZero();
    }

    @Test
    void disabledRecordsDisabledNotBypass() {
        properties.setEnabled(false);
        assertThat(engine.apply("13812345678", SensitiveType.PHONE, contextWith(MaskRole.USER)))
                .isEqualTo("13812345678");
        assertThat(recorder.lastAction()).isEqualTo(MaskAction.DISABLED);
    }

    @Test
    void nullCacheAndRecorderFallBackToNoOp() {
        MaskEngine isolated = new MaskEngine(
                properties,
                new MaskStrategyRegistry(List.of(new PhoneMaskStrategy())),
                null,
                new AlreadyMaskedDetector(),
                null
        );
        assertThat(isolated.apply("13812345678", SensitiveType.PHONE, contextWith(MaskRole.USER)))
                .isEqualTo("138****5678");
    }

    private MaskContext contextWith(MaskRole role) {
        properties.getDebug().setHeaderRoleEnabled(true);
        MaskContext.setHeaderRole(role);
        return new MaskContext(properties);
    }

    private static final class CountingCache implements MaskResultCache {
        private final AtomicInteger getCount = new AtomicInteger();
        private final AtomicInteger putCount = new AtomicInteger();

        @Override
        public String get(String typeCode, String raw) {
            getCount.incrementAndGet();
            return null;
        }

        @Override
        public void put(String typeCode, String raw, String masked) {
            putCount.incrementAndGet();
        }

        @Override
        public void invalidateAll() {
        }
    }

    private static final class RecordingRecorder implements MaskRecorder {
        private final List<MaskAction> actions = new ArrayList<>();

        @Override
        public void record(String typeCode, MaskRole role, MaskAction action, Duration duration) {
            actions.add(action);
        }

        MaskAction lastAction() {
            return actions.isEmpty() ? null : actions.getLast();
        }
    }
}
