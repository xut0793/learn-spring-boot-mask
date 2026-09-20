package com.learn.mask.tutorial.ch14.immediatewithoutcrypto;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用 {@link Map} 模拟数据库用户表：{@code userId → (fieldName → 库中明文)}。
 * <p>
 * 还原通道的明文只从这里读，不把展示接口里的打码串「解回去」。
 */
final class InMemoryUserStore {

    /** 用户主键到各敏感字段明文的索引。 */
    private final Map<Long, Map<String, String>> users;

    /**
     * @param users 初始数据，构造时会拷贝一份，避免外部修改
     */
    InMemoryUserStore(Map<Long, Map<String, String>> users) {
        Map<Long, Map<String, String>> copy = new ConcurrentHashMap<>();
        users.forEach((id, fields) -> copy.put(id, Map.copyOf(fields)));
        this.users = copy;
    }

    /** 教程内置样例：用户 {@code 1} 含 phone / idCard / email。 */
    static InMemoryUserStore demo() {
        return new InMemoryUserStore(Map.of(
                1L, Map.of(
                        "phone", "13812345678",
                        "idCard", "110101199001011234",
                        "email", "alice@example.com")));
    }

    /**
     * 按用户与字段取库中原文。
     *
     * @param userId 用户 id
     * @param field  列名 / JSON 字段名
     * @return 未打码的字符串
     * @throws UnmaskDeniedException 用户不存在或该用户没有此字段
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
