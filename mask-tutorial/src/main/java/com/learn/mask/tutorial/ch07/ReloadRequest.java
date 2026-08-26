package com.learn.mask.tutorial.ch07;

import java.util.Map;

/**
 * 热更新请求体。
 * <p>
 * 用**包装类型** {@code Boolean} 而不是 {@code boolean}，这样 {@code null}
 * 表示「这一项不改」，{@code false} 表示「改成关闭」。如果用基本类型，
 * 请求体里不传 {@code enabled} 会被反序列化成 {@code false}——
 * 一次「只想改手机号规则」的调用会顺手把整个脱敏组件关掉。
 * <p>
 * 这是所有「部分更新」接口的通用要求：**必须能区分「没传」和「传了假值」**。
 *
 * @param enabled 总开关，null 表示不改
 * @param rules   要覆盖的规则，键是类型编码。只出现在这个 Map 里的类型会被改，其余不动
 */
public record ReloadRequest(Boolean enabled, Map<String, RuleConfig> rules) {

    public static ReloadRequest ofRules(Map<String, RuleConfig> rules) {
        return new ReloadRequest(null, rules);
    }
}
