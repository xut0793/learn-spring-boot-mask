package com.learn.mask.tutorial.ch14.dialwithcrypto;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Map 模拟用户表；外呼 {@link DialTicketService#redeemForDial} 核销后从这里取真号拨号。
 */
final class InMemoryUserStore {

    /** {@code userId → (field → 库中明文)}。 */
    private final Map<Long, Map<String, String>> users;

    InMemoryUserStore(Map<Long, Map<String, String>> users) {
        Map<Long, Map<String, String>> copy = new ConcurrentHashMap<>();
        users.forEach((id, fields) -> copy.put(id, Map.copyOf(fields)));
        this.users = copy;
    }

    /** 教程内置用户 {@code 1} 的 phone / idCard 等。 */
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
