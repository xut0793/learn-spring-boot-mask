package com.learn.mask.crypto;

/**
 * 还原 / 外呼时按主体 + 字段读取库中原文。由业务提供 Bean。
 */
@FunctionalInterface
public interface SensitiveFieldLookup {

    /** 读取主体某字段的库中明文；不存在时可抛异常或返回 null，由业务定义。 */
    String read(String subjectId, String field);
}
