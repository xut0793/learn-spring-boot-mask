package com.learn.mask.crypto;

/**
 * 还原 / 外呼时按主体 + 字段读取库中原文。由业务提供 Bean。
 */
@FunctionalInterface
public interface SensitiveFieldLookup {

    String read(String subjectId, String field);
}
