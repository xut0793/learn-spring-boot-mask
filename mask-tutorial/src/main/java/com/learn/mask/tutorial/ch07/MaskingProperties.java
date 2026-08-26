package com.learn.mask.tutorial.ch07;

import com.learn.mask.tutorial.ch04.MaskRule;
import com.learn.mask.tutorial.ch04.MaskStrategyRegistry;
import com.learn.mask.tutorial.ch04.SensitiveType;
import com.learn.mask.tutorial.ch06.MaskSettings;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 脱敏配置，前缀 {@code masking}。
 * <p>
 * <b>本类的核心设计：可变容器 + 不可变快照。</b>
 * <ul>
 *   <li>{@link #rules} 是可变的，因为 Spring 绑定和热更新都需要往里写</li>
 *   <li>{@link #snapshot} 是不可变的 Map，引擎只读它。热更新时整体替换，
 *       而不是逐个字段修改</li>
 * </ul>
 * 这样读路径上永远不会看到「改了一半」的规则。理由和权衡见 7.5、7.7 节。
 * <p>
 * 实现 {@link MaskSettings} 而不是让引擎依赖本类，所以引擎完全不知道
 * 配置是从 YAML 来的还是内存里造的（第 6 章 6.2 节）。
 */
@ConfigurationProperties(prefix = "masking")
public class MaskingProperties implements MaskSettings, InitializingBean {

    private boolean enabled = true;

    private final RuleSet rules = new RuleSet();

    /** JSON/Map 的字段名 → 策略编码。Jackson 处理 Map 时用（第 9 章）。 */
    private Map<String, String> mapKeys = defaultMapKeys();

    /**
     * 规则版本号。每次规则变更递增，作为缓存 key 的一部分，
     * 让旧结果**自动**变得不可达，不依赖任何清理动作（7.6 节）。
     */
    private final AtomicLong ruleVersion = new AtomicLong(1);

    /**
     * 引擎实际读取的规则表。{@code volatile} 保证发布的可见性：
     * 一个线程整体替换引用，其他线程要么看到旧 Map、要么看到新 Map，
     * 不存在中间态。
     */
    private volatile Map<String, MaskRule> snapshot;

    public MaskingProperties() {
        // 先按默认值建一份，保证任何时刻 ruleOf() 都不会 NPE，
        // 包括「手动 new 出来还没调 afterPropertiesSet」的测试场景
        rebuildSnapshot();
    }

    /** Spring 完成属性绑定后重建快照。 */
    @Override
    public void afterPropertiesSet() {
        rebuildSnapshot();
    }

    /**
     * 把可变的 {@link RuleSet} 转成不可变快照并原子发布。
     * <p>
     * 热更新时必须调它，否则改了 {@code RuleSet} 引擎也看不见。
     */
    public void rebuildSnapshot() {
        Map<String, MaskRule> next = new HashMap<>();
        next.put(SensitiveType.PHONE.name(), rules.getPhone().toRule());
        next.put(SensitiveType.ID_CARD.name(), rules.getIdCard().toRule());
        next.put(SensitiveType.BANK_CARD.name(), rules.getBankCard().toRule());
        next.put(SensitiveType.EMAIL.name(), rules.getEmail().toRule());
        next.put(SensitiveType.CUSTOM.name(), rules.getCustom().toRule());
        rules.getExtras().forEach((code, config) -> {
            if (code != null && config != null) {
                next.put(MaskStrategyRegistry.normalize(code), config.toRule());
            }
        });
        // Map.copyOf 让快照真正不可变：即使有人拿到引用也改不了
        this.snapshot = Map.copyOf(next);
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 引擎唯一的规则读取入口。一次 volatile 读 + 一次哈希查找，没有锁。
     * <p>
     * 返回 {@code null} 表示没配这个类型的规则，引擎会旁路
     * （第 6 章 6.5 节讨论过这个行为的问题）。
     */
    @Override
    public MaskRule ruleOf(String code) {
        return snapshot.get(MaskStrategyRegistry.normalize(code));
    }

    public MaskRule ruleOf(SensitiveType type) {
        return ruleOf(type == null ? null : type.name());
    }

    /** 把 JSON/Map 的字段名解析成策略编码，认不出返回 null。 */
    public String typeCodeOf(String mapKey) {
        if (mapKey == null) {
            return null;
        }
        String code = mapKeys.get(mapKey);
        if (code == null || code.isBlank()) {
            return null;
        }
        return MaskStrategyRegistry.normalize(code);
    }

    public RuleSet getRules() {
        return rules;
    }

    public Map<String, String> getMapKeys() {
        return mapKeys;
    }

    public void setMapKeys(Map<String, String> mapKeys) {
        this.mapKeys = mapKeys == null ? new LinkedHashMap<>() : mapKeys;
    }

    public long getRuleVersion() {
        return ruleVersion.get();
    }

    public void bumpRuleVersion() {
        ruleVersion.incrementAndGet();
    }

    /** 当前快照的只读视图，供 {@code GET /rules} 之类的查询接口用。 */
    public Map<String, MaskRule> currentRules() {
        return snapshot;
    }

    private static Map<String, String> defaultMapKeys() {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("phone", SensitiveType.PHONE.name());
        // 同一个语义的多种命名风格都映射过去，因为 Map 的键来自
        // 上游 JSON，不受本项目的命名规范约束
        keys.put("idCard", SensitiveType.ID_CARD.name());
        keys.put("id_card", SensitiveType.ID_CARD.name());
        keys.put("identityCard", SensitiveType.ID_CARD.name());
        keys.put("email", SensitiveType.EMAIL.name());
        keys.put("bankCard", SensitiveType.BANK_CARD.name());
        keys.put("bank_card", SensitiveType.BANK_CARD.name());
        return keys;
    }
}
