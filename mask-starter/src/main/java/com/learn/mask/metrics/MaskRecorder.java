package com.learn.mask.metrics;

import com.learn.mask.context.MaskRole;
import com.learn.mask.engine.MaskAction;

import java.time.Duration;

/**
 * 记录一次脱敏调用的结果与耗时。引擎只依赖本接口，测试可换成记录到 List 的实现。
 */
public interface MaskRecorder {

    /** 记录一次 {@link com.learn.mask.engine.MaskEngine#apply} 的结果与耗时。 */
    void record(String typeCode, MaskRole role, MaskAction action, Duration duration);

    MaskRecorder NO_OP = (typeCode, role, action, duration) -> {
    };
}
