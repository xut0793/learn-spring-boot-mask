package com.learn.mask.tutorial.ch14;

import com.learn.mask.tutorial.ch05.MaskContext;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 还原用例：先鉴权、再按白名单从存储取明文。
 * <p>
 * 线程 A：{@link #withoutCrypto()}，不签发 token。
 * 线程 B 的半成品（与 Demo 相同）：把明文加密后挂在响应上，没有核销入口。
 * 要看 AES 真正办事，用 {@link UnmaskTicketService}。
 */
public class UnmaskService {

    private final ReversibleMasker masker;
    private final Set<String> reversibleFields;

    public UnmaskService(ReversibleMasker masker, Set<String> reversibleFields) {
        this.masker = masker;
        this.reversibleFields = Set.copyOf(reversibleFields);
    }

    /** 线程 A：只做授权还原，不碰 AES。 */
    public static UnmaskService withoutCrypto() {
        Set<String> fields = new LinkedHashSet<>();
        fields.add("phone");
        fields.add("idCard");
        return new UnmaskService(null, fields);
    }

    /** 线程 B：在线程 A 的结果上签发 token。 */
    public static UnmaskService demo(ReversibleMasker masker) {
        Set<String> fields = new LinkedHashSet<>();
        fields.add("phone");
        fields.add("idCard");
        return new UnmaskService(masker, fields);
    }

    public UnmaskResult unmask(MaskContext context, String field, String storedPlain) {
        if (context == null || !context.canUnmask()) {
            throw new UnmaskDeniedException("Current role cannot unmask");
        }
        if (field == null || !reversibleFields.contains(field)) {
            throw new UnmaskDeniedException("Field is not reversible: " + field);
        }
        String token = masker == null ? null : masker.encrypt(storedPlain);
        return new UnmaskResult(field, storedPlain, token);
    }

    public record UnmaskResult(String field, String value, String token) {
    }

    public static class UnmaskDeniedException extends RuntimeException {
        public UnmaskDeniedException(String message) {
            super(message);
        }
    }
}
