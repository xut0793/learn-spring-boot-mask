package com.learn.mask.web;

import com.learn.mask.annotation.SensitiveType;
import com.learn.mask.cache.MaskResultCache;
import com.learn.mask.config.MaskingProperties;
import com.learn.mask.config.RuleConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 规则热更新：写入可变 {@link RuleConfig}，再整体发布快照。
 * <p>
 * 四步顺序不可互换：写入 RuleSet → 重建快照 → 提版本号 → 清缓存。
 */
public class MaskingReloadService {

    private static final Logger log = LoggerFactory.getLogger(MaskingReloadService.class);

    private final MaskingProperties properties;
    private final MaskResultCache maskCache;
    private final Object reloadLock = new Object();

    public MaskingReloadService(MaskingProperties properties, MaskResultCache maskCache) {
        this.properties = properties;
        this.maskCache = maskCache;
    }

    public Map<String, Object> current() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", properties.isEnabled());
        body.put("ruleVersion", properties.getRuleVersion());
        body.put("channels", properties.getChannels());
        body.put("rules", properties.getRules());
        body.put("bypassRoles", properties.getBypassRoles());
        body.put("unmaskRoles", properties.getUnmaskRoles());
        return body;
    }

    public Map<String, Object> reload(ReloadRequest request) {
        synchronized (reloadLock) {
            if (request != null) {
                if (request.enabled() != null) {
                    boolean wasEnabled = properties.isEnabled();
                    properties.setEnabled(request.enabled());
                    if (wasEnabled && !request.enabled()) {
                        log.error("masking.enabled was turned off via reload; all sensitive fields will be returned in plaintext");
                    }
                }
                if (request.channels() != null) {
                    applyChannels(request.channels());
                }
                if (request.rules() != null && !request.rules().isEmpty()) {
                    request.rules().forEach(this::applyRule);
                }
            }
            properties.rebuildSnapshot();
            properties.bumpRuleVersion();
            maskCache.invalidateAll();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ruleVersion", properties.getRuleVersion());
            body.put("enabled", properties.isEnabled());
            body.put("channels", properties.getChannels());
            return body;
        }
    }

    private void applyRule(String key, RulePatch value) {
        if (value == null) {
            return;
        }
        RuleConfig existing = configOf(MaskingProperties.normalizeCode(key, null));
        if (value.keepPrefix() != null && value.keepPrefix() >= 0) {
            existing.setKeepPrefix(value.keepPrefix());
        }
        if (value.keepSuffix() != null && value.keepSuffix() >= 0) {
            existing.setKeepSuffix(value.keepSuffix());
        }
        if (value.maskChar() != null && !value.maskChar().isEmpty()) {
            existing.setMaskChar(value.maskChar().charAt(0));
        }
        if (value.enabled() != null) {
            existing.setEnabled(value.enabled());
        }
    }

    private RuleConfig configOf(String normalizedCode) {
        MaskingProperties.RuleSet rules = properties.getRules();
        try {
            return switch (SensitiveType.valueOf(normalizedCode)) {
                case PHONE -> rules.getPhone();
                case ID_CARD -> rules.getIdCard();
                case BANK_CARD -> rules.getBankCard();
                case EMAIL -> rules.getEmail();
                case CUSTOM -> rules.getCustom();
            };
        } catch (IllegalArgumentException ex) {
            return rules.getExtras().computeIfAbsent(normalizedCode, key -> new RuleConfig());
        }
    }

    private void applyChannels(MaskingProperties.Channels incoming) {
        MaskingProperties.Channels current = properties.getChannels();
        current.setJackson(incoming.isJackson());
        current.setLogback(incoming.isLogback());
        current.setAop(incoming.isAop());
        current.setMybatis(incoming.isMybatis());
        current.setStrict(incoming.isStrict());
    }

    public record ReloadRequest(
            Boolean enabled,
            MaskingProperties.Channels channels,
            Map<String, RulePatch> rules
    ) {
    }

    /**
     * 热更新补丁。包装类型为 null 表示这一项不改。
     */
    public record RulePatch(
            Boolean enabled,
            Integer keepPrefix,
            Integer keepSuffix,
            String maskChar
    ) {
    }
}
