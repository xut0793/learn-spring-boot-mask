package com.learn.mask.testsupport;

import com.learn.mask.context.MaskRole;
import com.learn.mask.engine.MaskAction;
import com.learn.mask.metrics.MaskRecorder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** 把每次打点记下来，用来断言引擎走了哪条分支。 */
public final class RecordingRecorder implements MaskRecorder {

    public record Record(String typeCode, MaskRole role, MaskAction action, Duration duration) {
    }

    private final List<Record> records = new ArrayList<>();

    @Override
    public void record(String typeCode, MaskRole role, MaskAction action, Duration duration) {
        records.add(new Record(typeCode, role, action, duration));
    }

    public List<MaskAction> actions() {
        return records.stream().map(Record::action).toList();
    }

    public MaskAction lastAction() {
        return records.isEmpty() ? null : records.getLast().action();
    }

    public Record last() {
        return records.isEmpty() ? null : records.getLast();
    }
}
