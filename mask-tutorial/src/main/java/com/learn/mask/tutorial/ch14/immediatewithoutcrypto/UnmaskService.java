package com.learn.mask.tutorial.ch14.immediatewithoutcrypto;

import com.learn.mask.tutorial.ch05.MaskContext;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 线程 A：不加 AES 的完整还原闭环。
 * <p>
 * 对应 {@code POST /api/unmask}：先 {@link MaskContext#canUnmask()}，再字段白名单，最后
 * {@link InMemoryUserStore} 查库。没有限时刷新、浏览器也不必持票，到此为止。
 */
public class UnmaskService {

    /** 允许走还原接口的字段名（教程用白名单代替 {@code @Sensitive(reversible=true)}）。 */
    private final Set<String> reversibleFields;

    /** 模拟数据库，存敏感列原文。 */
    private final InMemoryUserStore userStore;

    private UnmaskService(Set<String> reversibleFields, InMemoryUserStore userStore) {
        this.reversibleFields = Set.copyOf(reversibleFields);
        this.userStore = userStore;
    }

    /**
     * 教程默认实例：白名单 {@code phone}、{@code idCard}，内置 demo 用户数据。
     */
    public static UnmaskService create() {
        Set<String> fields = new LinkedHashSet<>();
        fields.add("phone");
        fields.add("idCard");
        return new UnmaskService(fields, InMemoryUserStore.demo());
    }

    /**
     * 执行一次授权还原。
     *
     * @param context 当前请求的脱敏上下文（含角色与 {@code canUnmask()}）
     * @param request 用户 id 与字段名
     * @return 库中明文及 echo 的 userId、field
     * @throws UnmaskDeniedException 无权、字段不可还原、或库中无数据
     */
    public UnmaskResult unmask(MaskContext context, UnmaskRequest request) {
        if (context == null || !context.canUnmask()) {
            throw new UnmaskDeniedException("Current role cannot unmask");
        }
        if (request == null || request.field() == null || !reversibleFields.contains(request.field())) {
            throw new UnmaskDeniedException("Field is not reversible: " + (request == null ? null : request.field()));
        }
        String value = userStore.readField(request.userId(), request.field());
        return new UnmaskResult(request.userId(), request.field(), value);
    }

    /**
     * 还原接口响应体（线程 A 无 token 字段）。
     *
     * @param userId 用户 id
     * @param field  字段名
     * @param value  库中明文
     */
    public record UnmaskResult(long userId, String field, String value) {
    }
}
