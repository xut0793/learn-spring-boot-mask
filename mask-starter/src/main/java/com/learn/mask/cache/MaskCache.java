package com.learn.mask.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.learn.mask.annotation.SensitiveType;
import com.learn.mask.config.MaskingProperties;

import java.time.Duration;

/**
 * 明文 → 脱敏结果缓存。键包含规则版本，热更新后旧结果自动失效。
 */
public class MaskCache {

    private final MaskingProperties properties;
    private final Cache<String, String> cache;

    public MaskCache(MaskingProperties properties) {
        this.properties = properties;
        this.cache = Caffeine.newBuilder()
                .maximumSize(Math.max(properties.getCache().getMaxSize(), 1))
                .expireAfterAccess(Duration.ofMinutes(Math.max(properties.getCache().getExpireAfterAccessMinutes(), 1)))
                .build();
    }

    public String get(SensitiveType type, String raw) {
        return get(type == null ? null : type.name(), raw);
    }

    public String get(String typeCode, String raw) {
        if (!properties.getCache().isEnabled() || raw == null) {
            return null;
        }
        return cache.getIfPresent(key(typeCode, raw));
    }

    public void put(SensitiveType type, String raw, String masked) {
        put(type == null ? null : type.name(), raw, masked);
    }

    public void put(String typeCode, String raw, String masked) {
        if (!properties.getCache().isEnabled() || raw == null || masked == null) {
            return;
        }
        cache.put(key(typeCode, raw), masked);
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }

    private String key(String typeCode, String raw) {
        return properties.getRuleVersion() + ":" + MaskingProperties.normalizeCode(typeCode, null) + ":" + raw;
    }
}
