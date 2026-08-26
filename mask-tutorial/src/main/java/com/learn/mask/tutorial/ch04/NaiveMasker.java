package com.learn.mask.tutorial.ch04;

/**
 * 第 4 章的反面教材：一步步暴露「直接写打码逻辑」的问题。
 * <p>
 * 这个类**不是**要你抄的代码，它存在的意义是让 {@code NaiveMaskerTest} 能把每个 bug
 * 断言出来。看完测试再去看 {@link MaskStrategy} 体系，才能理解那些设计不是过度设计。
 */
public final class NaiveMasker {

    private NaiveMasker() {
    }

    /**
     * 第一版：用 {@code String.replace} 打码。
     * <p>
     * bug：{@code replace} 替换的是**所有**匹配片段，不是指定位置。
     * 手机号里出现两次相同的四位数字时结果就错了。
     */
    public static String maskPhoneByReplace(String phone) {
        return phone.replace(phone.substring(3, 7), "****");
    }

    /**
     * 第二版：改成按位置拼接，替换位置的问题解决了。
     * <p>
     * bug：对 null、空串、长度不足 7 的输入直接抛异常。
     */
    public static String maskPhoneBySubstring(String phone) {
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /**
     * 第三版：支持多种类型，于是 if-else 开始生长。
     * <p>
     * 问题不再是某个 bug，而是结构：每加一种类型就要改这个方法，
     * 每个分支都要重复处理空值与长度，星号数量在不定长字段上还得各自计算。
     */
    public static String maskByType(String type, String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        if ("phone".equals(type)) {
            return keep(raw, 3, 4);
        } else if ("idCard".equals(type)) {
            return keep(raw, 6, 4);
        } else if ("bankCard".equals(type)) {
            return keep(raw, 4, 4);
        } else if ("email".equals(type)) {
            int at = raw.indexOf('@');
            if (at <= 0) {
                return keep(raw, 1, 0);
            }
            return raw.charAt(0) + "*".repeat(at - 1) + raw.substring(at);
        }
        return keep(raw, 1, 1);
    }

    private static String keep(String raw, int prefix, int suffix) {
        int len = raw.length();
        if (len <= prefix + suffix) {
            return "*".repeat(len);
        }
        return raw.substring(0, prefix) + "*".repeat(len - prefix - suffix) + raw.substring(len - suffix);
    }
}
