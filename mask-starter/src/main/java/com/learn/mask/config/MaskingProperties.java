package com.learn.mask.config;

import com.learn.mask.annotation.SensitiveType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 脱敏总开关、通道、角色、规则与缓存配置，前缀为 {@code masking}。规则变更后调用 {@link #bumpRuleVersion()} 使缓存失效。
 */
@ConfigurationProperties(prefix = "masking")
public class MaskingProperties {

    private boolean enabled = true;
    private final Channels channels = new Channels();
    private List<String> bypassRoles = new ArrayList<>(List.of("ADMIN"));
    private List<String> unmaskRoles = new ArrayList<>(List.of("ADMIN", "CS"));
    private final Cache cache = new Cache();
    private final Debug debug = new Debug();
    private final Reversible reversible = new Reversible();
    private final RuleSet rules = new RuleSet();
    private Map<String, SensitiveType> mapKeys = defaultMapKeys();
    /**
     * 业务自定义类型的 Map 键映射，例如 {@code trackingNo: YOUR_TYPE}。
     * starter 枚举中不存在的编码走 {@code masking.rules.extras}。
     */
    private Map<String, String> extraMapKeys = new LinkedHashMap<>();
    private final AtomicLong ruleVersion = new AtomicLong(1);

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

    public void bumpRuleVersion() {
        ruleVersion.incrementAndGet();
    }

    /** 按内置类型取对应规则。 */
    public MaskRule ruleOf(SensitiveType type) {
        SensitiveType resolved = type == null ? SensitiveType.CUSTOM : type;
        return switch (resolved) {
            case PHONE -> rules.getPhone();
            case ID_CARD -> rules.getIdCard();
            case BANK_CARD -> rules.getBankCard();
            case EMAIL -> rules.getEmail();
            case CUSTOM -> rules.getCustom();
            case ADDRESS -> rules.getAddress();
        };
    }

    /**
     * 按类型编码取规则。先查 {@code masking.rules.extras}，再回落到内置枚举。
     */
    public MaskRule ruleOf(String code) {
        String normalized = normalizeCode(code, null);
        MaskRule extra = extraRule(normalized);
        if (extra != null) {
            return extra;
        }
        try {
            return ruleOf(SensitiveType.valueOf(normalized));
        } catch (IllegalArgumentException ex) {
            return rules.getExtras().computeIfAbsent(normalized, key -> rule(1, 1));
        }
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

    public static String normalizeCode(String code, SensitiveType type) {
        if (code != null && !code.isBlank()) {
            return code.trim().toUpperCase(Locale.ROOT);
        }
        return (type == null ? SensitiveType.CUSTOM : type).name();
    }

    private MaskRule extraRule(String normalizedCode) {
        Map<String, MaskRule> extras = rules.getExtras();
        if (extras == null || extras.isEmpty()) {
            return null;
        }
        MaskRule direct = extras.get(normalizedCode);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, MaskRule> entry : extras.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(normalizedCode)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** 各类型保留前后缀规则，对应 YAML {@code masking.rules}。 */
    public static class RuleSet {
        private MaskRule phone = rule(3, 4);
        private MaskRule idCard = rule(6, 4);
        private MaskRule bankCard = rule(4, 4);
        private MaskRule email = rule(1, 0);
        private MaskRule custom = defaultCustomRule();
        private MaskRule address = rule(3, 0);
        /** 业务自定义类型规则，键为策略编码，与 {@code MaskStrategy#code()} 对应。 */
        private Map<String, MaskRule> extras = new LinkedHashMap<>();

        public MaskRule getPhone() {
            return phone;
        }

        public void setPhone(MaskRule phone) {
            this.phone = phone;
        }

        public MaskRule getIdCard() {
            return idCard;
        }

        public void setIdCard(MaskRule idCard) {
            this.idCard = idCard;
        }

        public MaskRule getBankCard() {
            return bankCard;
        }

        public void setBankCard(MaskRule bankCard) {
            this.bankCard = bankCard;
        }

        public MaskRule getEmail() {
            return email;
        }

        public void setEmail(MaskRule email) {
            this.email = email;
        }

        public MaskRule getCustom() {
            return custom;
        }

        public void setCustom(MaskRule custom) {
            this.custom = custom;
        }

        public MaskRule getAddress() {
            return address;
        }

        public void setAddress(MaskRule address) {
            this.address = address;
        }

        public Map<String, MaskRule> getExtras() {
            return extras;
        }

        public void setExtras(Map<String, MaskRule> extras) {
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
        keys.put("detail", SensitiveType.ADDRESS);
        keys.put("addressDetail", SensitiveType.ADDRESS);
        keys.put("address_detail", SensitiveType.ADDRESS);
        return keys;
    }

    private static MaskRule defaultCustomRule() {
        return rule(1, 1);
    }

    private static MaskRule rule(int prefix, int suffix) {
        MaskRule rule = new MaskRule();
        rule.setKeepPrefix(prefix);
        rule.setKeepSuffix(suffix);
        return rule;
    }

    /** 四个切点开关。Jackson 与 AOP/MyBatis 同时开启时由 {@link MaskingChannelValidator} 告警或失败。 */
    public static class Channels {
        private boolean jackson = true;
        private boolean logback = true;
        private boolean aop = false;
        private boolean mybatis = false;
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
        private boolean enabled = true;
        private long maxSize = 10_000;
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
        private String secretKey = "demo-key-not-for-prod-32b!!";

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
    }
}
