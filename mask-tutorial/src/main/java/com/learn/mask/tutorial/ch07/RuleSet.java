package com.learn.mask.tutorial.ch07;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 对应 YAML 的 {@code masking.rules}。
 * <p>
 * 内置类型用具名字段，业务自定义类型用 {@link #extras} 这个 Map。
 * <p>
 * 为什么不全都放进一个 Map？因为具名字段能让 IDE 补全和
 * {@code spring-configuration-metadata.json} 生效——写配置时打
 * {@code masking.rules.} 会弹出候选，写错会有黄色波浪线。
 * Map 的键是任意字符串，IDE 帮不上忙。
 * <p>
 * 代价是内置类型和自定义类型的配置写法不一致（见 7.4 节的讨论）。
 */
public class RuleSet {

    private RuleConfig phone = new RuleConfig(3, 4);
    private RuleConfig idCard = new RuleConfig(6, 4);
    private RuleConfig bankCard = new RuleConfig(4, 4);
    private RuleConfig email = new RuleConfig(1, 0);
    private RuleConfig custom = new RuleConfig(1, 1);

    /** 键是策略编码，与 {@code MaskStrategy#code()} 对应，例如 {@code EXPRESS}。 */
    private Map<String, RuleConfig> extras = new LinkedHashMap<>();

    public RuleConfig getPhone() {
        return phone;
    }

    public void setPhone(RuleConfig phone) {
        this.phone = phone;
    }

    public RuleConfig getIdCard() {
        return idCard;
    }

    public void setIdCard(RuleConfig idCard) {
        this.idCard = idCard;
    }

    public RuleConfig getBankCard() {
        return bankCard;
    }

    public void setBankCard(RuleConfig bankCard) {
        this.bankCard = bankCard;
    }

    public RuleConfig getEmail() {
        return email;
    }

    public void setEmail(RuleConfig email) {
        this.email = email;
    }

    public RuleConfig getCustom() {
        return custom;
    }

    public void setCustom(RuleConfig custom) {
        this.custom = custom;
    }

    public Map<String, RuleConfig> getExtras() {
        return extras;
    }

    public void setExtras(Map<String, RuleConfig> extras) {
        // 防御 null：YAML 里写了 extras: 但没给内容时，绑定结果是 null
        this.extras = extras == null ? new LinkedHashMap<>() : extras;
    }
}
