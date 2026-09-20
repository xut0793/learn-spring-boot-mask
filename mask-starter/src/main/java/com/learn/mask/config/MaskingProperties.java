package com.learn.mask.config;

import com.learn.mask.annotation.SensitiveType;
import com.learn.mask.strategy.MaskStrategyRegistry;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 脱敏总开关、通道、角色、规则与缓存配置，前缀为 {@code masking}。
 * <p>
 * 写路径改可变的 {@link RuleSet}；读路径只看 {@link #snapshot}。
 * 热更新必须先 {@link #rebuildSnapshot()}，再 {@link #bumpRuleVersion()}。
 */
@ConfigurationProperties(prefix = "masking")
public class MaskingProperties implements InitializingBean, MaskSettings {

    /** 总开关；关闭后所有通道原样输出。 */
    private boolean enabled = true;
    private final Channels channels = new Channels();
    /** 命中则 {@link com.learn.mask.context.MaskContext#shouldBypass()} 为 true，出口明文。 */
    private List<String> bypassRoles = new ArrayList<>(List.of("ADMIN"));
    /** 命中则允许调用可逆还原接口。 */
    private List<String> unmaskRoles = new ArrayList<>(List.of("ADMIN", "CS"));
    private final Cache cache = new Cache();
    private final Debug debug = new Debug();
    private final Reversible reversible = new Reversible();
    private final RuleSet rules = new RuleSet();
    /** Jackson / Map 出口：JSON 字段名 → 内置 {@link SensitiveType}。 */
    private Map<String, SensitiveType> mapKeys = defaultMapKeys();
    /**
     * 业务自定义类型的 Map 键映射，例如 {@code trackingNo: YOUR_TYPE}。
     * starter 枚举中不存在的编码走 {@code masking.rules.extras}。
     */
    private Map<String, String> extraMapKeys = new LinkedHashMap<>();
    private final AtomicLong ruleVersion = new AtomicLong(1);
    /**
     * 引擎实际读取的规则表。{@code volatile} 保证整体替换可见：
     * 其他线程要么看到旧 Map，要么看到新 Map。
     */
    private volatile Map<String, MaskRule> snapshot;

    public MaskingProperties() {
        rebuildSnapshot();
    }

    /** Spring 绑定完成后发布首版规则快照。 */
    @Override
    public void afterPropertiesSet() {
        rebuildSnapshot();
    }

    /**
     * 把可变 {@link RuleSet} 转成不可变快照并发布。
     * 热更新写入 {@code RuleSet} 后必须调用，否则引擎仍读旧规则。
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
                next.put(normalizeCode(code, null), config.toRule());
            }
        });
        this.snapshot = Map.copyOf(next);
    }

    /** 当前快照的只读视图。 */
    public Map<String, MaskRule> currentRules() {
        return snapshot;
    }

    /** 是否开启脱敏总开关。 */
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Channels getChannels() {
        return channels;
    }

    public List<String> getBypassRoles() {
        return bypassRoles;
    }

    public void setBypassRoles(List<String> bypassRoles) {
        this.bypassRoles = bypassRoles;
    }

    public List<String> getUnmaskRoles() {
        return unmaskRoles;
    }

    public void setUnmaskRoles(List<String> unmaskRoles) {
        this.unmaskRoles = unmaskRoles;
    }

    public Cache getCache() {
        return cache;
    }

    public Debug getDebug() {
        return debug;
    }

    public Reversible getReversible() {
        return reversible;
    }

    public RuleSet getRules() {
        return rules;
    }

    public Map<String, SensitiveType> getMapKeys() {
        return mapKeys;
    }

    public void setMapKeys(Map<String, SensitiveType> mapKeys) {
        this.mapKeys = mapKeys;
    }

    public Map<String, String> getExtraMapKeys() {
        return extraMapKeys;
    }

    public void setExtraMapKeys(Map<String, String> extraMapKeys) {
        this.extraMapKeys = extraMapKeys == null ? new LinkedHashMap<>() : extraMapKeys;
    }

    public long getRuleVersion() {
        return ruleVersion.get();
    }

    /** 热更新后递增，使 {@link com.learn.mask.cache.MaskCache} 的复合键失效。 */
    public void bumpRuleVersion() {
        ruleVersion.incrementAndGet();
    }

    /** 按内置类型取快照中的规则。 */
    public MaskRule ruleOf(SensitiveType type) {
        return ruleOf(type == null ? null : type.name());
    }

    /**
     * 按类型编码取规则。只读快照，一次 volatile 读加一次哈希查找。
     * 未命中时回落 {@link SensitiveType#CUSTOM}，与策略表一致，避免未知 code 漏脱。
     */
    public MaskRule ruleOf(String code) {
        String normalized = normalizeCode(code, null);
        MaskRule found = snapshot.get(normalized);
        if (found != null) {
            return found;
        }
        return snapshot.get(SensitiveType.CUSTOM.name());
    }

    /**
     * 将 JSON/Map 字段名解析为策略编码；内置 {@code map-keys} 优先，其次 {@code extra-map-keys}。
     */
    public String typeCodeOf(String mapKey) {
        if (mapKey == null) {
            return null;
        }
        SensitiveType type = mapKeys.get(mapKey);
        if (type != null) {
            return type.name();
        }
        String extra = extraMapKeys.get(mapKey);
        if (extra == null || extra.isBlank()) {
            return null;
        }
        return normalizeCode(extra, null);
    }

    /**
     * 编码归一化，委托 {@link MaskStrategyRegistry#resolve(String, SensitiveType)}，
     * 与策略表共用同一实现。
     */
    public static String normalizeCode(String code, SensitiveType type) {
        return MaskStrategyRegistry.resolve(code, type);
    }

    /** 各类型保留前后缀规则，对应 YAML {@code masking.rules}。只给绑定和热更新写。 */
    public static class RuleSet {
        private RuleConfig phone = new RuleConfig(3, 4);
        private RuleConfig idCard = new RuleConfig(6, 4);
        private RuleConfig bankCard = new RuleConfig(4, 4);
        private RuleConfig email = new RuleConfig(1, 0);
        /** {@link SensitiveType#CUSTOM} 及未单独列出的类型的默认规则。 */
        private RuleConfig custom = new RuleConfig(1, 1);
        /** 业务自定义类型规则，键为策略编码，与 {@code MaskStrategy#code()} 对应。 */
        private Map<String, RuleConfig> extras = new LinkedHashMap<>();

        public RuleConfig getPhone() {
            return phone;
        }

        public void setPhone(RuleConfig phone) {
            this.phone = phone == null ? new RuleConfig(3, 4) : phone;
        }

        public RuleConfig getIdCard() {
            return idCard;
        }

        public void setIdCard(RuleConfig idCard) {
            this.idCard = idCard == null ? new RuleConfig(6, 4) : idCard;
        }

        public RuleConfig getBankCard() {
            return bankCard;
        }

        public void setBankCard(RuleConfig bankCard) {
            this.bankCard = bankCard == null ? new RuleConfig(4, 4) : bankCard;
        }

        public RuleConfig getEmail() {
            return email;
        }

        public void setEmail(RuleConfig email) {
            this.email = email == null ? new RuleConfig(1, 0) : email;
        }

        public RuleConfig getCustom() {
            return custom;
        }

        public void setCustom(RuleConfig custom) {
            this.custom = custom == null ? new RuleConfig(1, 1) : custom;
        }

        public Map<String, RuleConfig> getExtras() {
            return extras;
        }

        public void setExtras(Map<String, RuleConfig> extras) {
            this.extras = extras == null ? new LinkedHashMap<>() : extras;
        }
    }

    private static Map<String, SensitiveType> defaultMapKeys() {
        Map<String, SensitiveType> keys = new LinkedHashMap<>();
        keys.put("phone", SensitiveType.PHONE);
        keys.put("identityCard", SensitiveType.ID_CARD);
        keys.put("idCard", SensitiveType.ID_CARD);
        keys.put("id_card", SensitiveType.ID_CARD);
        keys.put("id-card", SensitiveType.ID_CARD);
        keys.put("email", SensitiveType.EMAIL);
        keys.put("bankCard", SensitiveType.BANK_CARD);
        keys.put("bank_card", SensitiveType.BANK_CARD);
        return keys;
    }

    /** 四个切点开关。Jackson 与 AOP/MyBatis 同时开启时由 {@link MaskingChannelValidator} 告警或失败。 */
    public static class Channels {
        private boolean jackson = true;
        private boolean logback = true;
        private boolean aop = false;
        private boolean mybatis = false;
        /** 为 true 时 Jackson 与 AOP/MyBatis 同时开启会在启动阶段失败。 */
        private boolean strict = false;

        public boolean isJackson() {
            return jackson;
        }

        public void setJackson(boolean jackson) {
            this.jackson = jackson;
        }

        public boolean isLogback() {
            return logback;
        }

        public void setLogback(boolean logback) {
            this.logback = logback;
        }

        public boolean isAop() {
            return aop;
        }

        public void setAop(boolean aop) {
            this.aop = aop;
        }

        public boolean isMybatis() {
            return mybatis;
        }

        public void setMybatis(boolean mybatis) {
            this.mybatis = mybatis;
        }

        public boolean isStrict() {
            return strict;
        }

        public void setStrict(boolean strict) {
            this.strict = strict;
        }
    }

    /** 明文到脱敏结果的本地缓存。 */
    public static class Cache {
        private boolean enabled = false;
        /** Caffeine 最大条目数。 */
        private long maxSize = 10_000;
        /** 访问后过期时间（分钟）。 */
        private long expireAfterAccessMinutes = 10;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(long maxSize) {
            this.maxSize = maxSize;
        }

        public long getExpireAfterAccessMinutes() {
            return expireAfterAccessMinutes;
        }

        public void setExpireAfterAccessMinutes(long expireAfterAccessMinutes) {
            this.expireAfterAccessMinutes = expireAfterAccessMinutes;
        }
    }

    /** 用请求头覆盖角色，仅建议在联调时打开。 */
    public static class Debug {
        private boolean headerRoleEnabled = false;
        /** 与 {@link HeaderRoleFilter} 读取的请求头名一致。 */
        private String headerName = "X-User-Role";

        public boolean isHeaderRoleEnabled() {
            return headerRoleEnabled;
        }

        public void setHeaderRoleEnabled(boolean headerRoleEnabled) {
            this.headerRoleEnabled = headerRoleEnabled;
        }

        public String getHeaderName() {
            return headerName;
        }

        public void setHeaderName(String headerName) {
            this.headerName = headerName;
        }
    }

    /** AES-GCM 可逆脱敏密钥。接口仍输出星号，还原接口用令牌换明文。 */
    public static class Reversible {
        private boolean enabled = true;
        /** AES 密钥，UTF-8 长度须为 16 或 32 字节。 */
        private String secretKey = "demo-key-not-for-production-use!";
        /** 允许签发还原票据的字段名（大小写不敏感）。 */
        private List<String> fields = new ArrayList<>(List.of("phone", "idCard", "identityCard"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public List<String> getFields() {
            return fields;
        }

        public void setFields(List<String> fields) {
            this.fields = fields == null ? new ArrayList<>() : fields;
        }

        /** 字段是否在可逆白名单内。 */
        public boolean allows(String field) {
            if (field == null || fields == null || fields.isEmpty()) {
                return false;
            }
            return fields.stream().anyMatch(item -> item != null && item.equalsIgnoreCase(field));
        }
    }
}
