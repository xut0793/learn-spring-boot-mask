package com.learn.mask.tutorial.ch06;

import com.learn.mask.tutorial.ch05.MaskRole;

import java.time.Duration;

/**
 * 记录一次脱敏调用的结果与耗时。
 * <p>
 * 第 8 章会有一个 Micrometer 实现。本章用它来做两件事：
 * <ul>
 *   <li>{@link #NO_OP} 让引擎在没有监控体系时也能跑</li>
 *   <li>测试里换成一个记录到 List 的实现，就能断言「引擎走了哪条分支」</li>
 * </ul>
 */
public interface MaskRecorder {

    void record(String typeCode, MaskRole role, MaskAction action, Duration duration);

    MaskRecorder NO_OP = (typeCode, role, action, duration) -> {
    };
}
