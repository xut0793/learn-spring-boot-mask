package com.learn.mask.tutorial.ch08;

import com.learn.mask.tutorial.ch05.MaskRole;
import com.learn.mask.tutorial.ch06.MaskAction;
import com.learn.mask.tutorial.ch06.MaskRecorder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.Locale;

/**
 * 把引擎的每次调用打成 Micrometer 指标。
 * <p>
 * 指标设计的三个决定，都在 8.7 节详述：
 * <ul>
 *   <li>标签只用 {@code type} / {@code role} / {@code result} 三个**低基数**维度，
 *       绝不放字段名、用户 ID、明文</li>
 *   <li>{@code result} 的值统一小写，{@code type} / {@code role} 保持大写</li>
 *   <li>除了总量指标，还给 fail / bypass / skipped 单独开了计数器，
 *       让告警规则不必依赖标签过滤</li>
 * </ul>
 */
public class MicrometerMaskRecorder implements MaskRecorder {

    static final String INVOKE = "masking.invoke";
    static final String DURATION = "masking.duration";
    static final String FAIL = "masking.fail";
    static final String BYPASS = "masking.bypass";
    static final String SKIPPED = "masking.skipped";

    private static final String UNKNOWN = "unknown";

    private final MeterRegistry registry;

    public MicrometerMaskRecorder(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void record(String typeCode, MaskRole role, MaskAction action, Duration duration) {
        // 三个标签都要防空。Micrometer 遇到 null 标签值会抛异常，
        // 而「指标打点把业务请求搞挂了」是绝对不能接受的
        String typeTag = (typeCode == null || typeCode.isBlank()) ? UNKNOWN : typeCode;
        String roleTag = role == null ? UNKNOWN : role.name();
        String resultTag = action == null ? UNKNOWN : action.name().toLowerCase(Locale.ROOT);

        Counter.builder(INVOKE)
                .tag("type", typeTag)
                .tag("role", roleTag)
                .tag("result", resultTag)
                .register(registry)
                .increment();

        // duration 上刻意**不带** result 标签：
        // 加上会让时间序列数量翻四倍，而「旁路的耗时」本身没有分析价值
        Timer.builder(DURATION)
                .tag("type", typeTag)
                .tag("role", roleTag)
                .register(registry)
                .record(duration);

        if (action == MaskAction.FAIL) {
            Counter.builder(FAIL).tag("type", typeTag).register(registry).increment();
        }
        if (action == MaskAction.BYPASS) {
            Counter.builder(BYPASS).tag("role", roleTag).register(registry).increment();
        }
        if (action == MaskAction.SKIP_ALREADY_MASKED) {
            Counter.builder(SKIPPED).tag("type", typeTag).register(registry).increment();
        }
    }
}
