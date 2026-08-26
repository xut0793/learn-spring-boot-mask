package com.learn.mask.tutorial.ch06;

import com.learn.mask.tutorial.ch05.MaskRole;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 测试用的 {@link MaskRecorder}：把每次调用记下来，好断言引擎走了哪条分支。
 * <p>
 * 只断言返回值是不够的——比如「实际脱敏」和「缓存命中」的返回值完全一样。
 */
class RecordingRecorder implements MaskRecorder {

    record Entry(String typeCode, MaskRole role, MaskAction action, Duration duration) {
    }

    private final List<Entry> entries = new ArrayList<>();

    @Override
    public void record(String typeCode, MaskRole role, MaskAction action, Duration duration) {
        entries.add(new Entry(typeCode, role, action, duration));
    }

    List<Entry> entries() {
        return entries;
    }

    Entry last() {
        return entries.get(entries.size() - 1);
    }

    MaskAction lastAction() {
        return last().action();
    }

    List<MaskAction> actions() {
        return entries.stream().map(Entry::action).toList();
    }

    void clear() {
        entries.clear();
    }
}
