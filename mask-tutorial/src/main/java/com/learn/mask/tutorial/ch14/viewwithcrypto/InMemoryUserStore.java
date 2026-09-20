package com.learn.mask.tutorial.ch14.viewwithcrypto;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Map 模拟用户表；核销/刷新后仍从这里读明文，票里不存手机号。
 */
final class InMemoryUserStore {

    /** {@code userId → (field → 库中明文)}。 */
    private final Map<Long, Map<String, String>> users;

    InMemoryUserStore(Map<Long, Map<String, String>> users) {
        Map<Long, Map<String, String>> copy = new ConcurrentHashMap<>();
        users.forEach((id, fields) -> copy.put(id, Map.copyOf(fields)));
        this.users = copy;
    }

    /** 与 {@link com.learn.mask.tutorial.ch14.immediatewithoutcrypto.InMemoryUserStore#demo()} 相同样例数据。 */
    static InMemoryUserStore demo() {
        return new InMemoryUserStore(Map.of(
                1L, Map.of(
                        "phone", "13812345678",
                        "idCard", "110101199003078515",
                        "email", "alice@example.com")));
    }

    /**
     * @throws UnmaskDeniedException 用户或字段不存在
     */
    String readField(long userId, String field) {
        Map<String, String> fields = users.get(userId);
        if (fields == null) {
            throw new UnmaskDeniedException("User not found: " + userId);
        }
        String value = fields.get(field);
        if (value == null) {
            throw new UnmaskDeniedException("No such field: " + userId + "." + field);
        }
        return value;
    }
}
