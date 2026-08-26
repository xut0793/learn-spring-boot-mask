package com.learn.mask.tutorial.ch08;

import java.time.Duration;

/**
 * 缓存配置。
 *
 * @param enabled            关掉后 {@link CaffeineMaskCache} 变成 no-op
 * @param maxSize            最大条目数。这是**条目数**不是字节数，容量规划见第 8 章 8.4 节
 * @param expireAfterAccess  多久没被访问就淘汰。用 access 而不是 write 的理由见 8.5 节
 */
public record CacheOptions(boolean enabled, long maxSize, Duration expireAfterAccess) {

    public static CacheOptions defaults() {
        return new CacheOptions(true, 10_000, Duration.ofMinutes(10));
    }

    public static CacheOptions disabled() {
        return new CacheOptions(false, 10_000, Duration.ofMinutes(10));
    }

    /** 钳制到合法范围。配置成 0 或负数时 Caffeine 会抛异常或行为退化。 */
    public CacheOptions sanitized() {
        long size = Math.max(maxSize, 1);
        Duration ttl = expireAfterAccess == null || expireAfterAccess.isZero() || expireAfterAccess.isNegative()
                ? Duration.ofMinutes(1)
                : expireAfterAccess;
        return new CacheOptions(enabled, size, ttl);
    }
}
