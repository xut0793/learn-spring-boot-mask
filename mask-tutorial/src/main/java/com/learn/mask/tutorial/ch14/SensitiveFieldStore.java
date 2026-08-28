package com.learn.mask.tutorial.ch14;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 敏感字段的权威存储。票据核销后仍从这里读明文，不相信 token 里有没有号码。
 */
public interface SensitiveFieldStore {

    String read(String subjectId, String field);

    static SensitiveFieldStore memory(Map<String, Map<String, String>> data) {
        Map<String, Map<String, String>> copy = new ConcurrentHashMap<>();
        data.forEach((id, fields) -> copy.put(id, new ConcurrentHashMap<>(fields)));
        return (subjectId, field) -> {
            Map<String, String> fields = copy.get(subjectId);
            if (fields == null || !fields.containsKey(field)) {
                throw new UnmaskService.UnmaskDeniedException("No such field: " + subjectId + "." + field);
            }
            return fields.get(field);
        };
    }
}
