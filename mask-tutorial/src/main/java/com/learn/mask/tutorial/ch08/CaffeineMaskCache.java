package com.learn.mask.tutorial.ch08;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.learn.mask.tutorial.ch04.MaskStrategyRegistry;
import com.learn.mask.tutorial.ch06.MaskResultCache;

import java.util.function.LongSupplier;

/**
 * 明文 → 打码结果的本地缓存。
 * <p>
 * <b>为什么这个缓存是安全的：key 里是明文、value 是打码值。</b>
 * 明文出现在 key 里，是这个设计里唯一需要警惕的地方（8.6 节会讨论）。
 * 反过来（key 是打码值、value 是明文）就是一个存明文的表，绝对不能这么做。
 * <p>
 * <b>为什么 key 里要带规则版本：</b>规则一改，所有旧 key 立刻不可达，
 * 不依赖任何清理动作。见第 7 章 7.6 节。
 */
public class CaffeineMaskCache implements MaskResultCache {

    private final CacheOptions options;
    private final LongSupplier ruleVersion;
    private final Cache<String, String> cache;

    /**
     * @param ruleVersion 规则版本号的取值函数。用 {@link LongSupplier} 而不是直接依赖
     *                    {@code MaskingProperties}，缓存就不知道版本号是从哪来的，
     *                    测试里传一个可变的 lambda 就能模拟热更新
     */
    public CaffeineMaskCache(CacheOptions options, LongSupplier ruleVersion) {
        this(options, ruleVersion, Ticker.systemTicker());
    }

    /**
     * 可注入时钟的构造函数，供测试验证过期行为。
     * <p>
     * 如果不留这个口子，测过期就只能 {@code Thread.sleep(10 分钟)}——
     * 于是这条分支永远不会被测到。**「时间」是最典型的需要被注入的依赖。**
     */
    public CaffeineMaskCache(CacheOptions options, LongSupplier ruleVersion, Ticker ticker) {
        this.options = options.sanitized();
        this.ruleVersion = ruleVersion;
        this.cache = Caffeine.newBuilder()
                .maximumSize(this.options.maxSize())
                .expireAfterAccess(this.options.expireAfterAccess())
                .ticker(ticker)
                // 不开 recordStats 的话 hitRate 永远是 0，
                // 也就无法回答「缓存配得对不对」，见 8.4 节
                .recordStats()
                .build();
    }

    @Override
    public String get(String typeCode, String raw) {
        if (!options.enabled() || raw == null) {
            return null;
        }
        return cache.getIfPresent(key(typeCode, raw));
    }

    @Override
    public void put(String typeCode, String raw, String masked) {
        // masked == null 也不写：null 值会让 getIfPresent 的「未命中」和
        // 「命中了一个 null」无法区分，而 Caffeine 本身不允许 null value
        if (!options.enabled() || raw == null || masked == null) {
            return;
        }
        cache.put(key(typeCode, raw), masked);
    }

    @Override
    public void invalidateAll() {
        cache.invalidateAll();
    }

    /** 供指标绑定用，见 {@link MaskCacheMetrics}。 */
    public CacheStats stats() {
        return cache.stats();
    }

    public long estimatedSize() {
        cache.cleanUp();
        return cache.estimatedSize();
    }

    private String key(String typeCode, String raw) {
        return ruleVersion.getAsLong() + ":" + MaskStrategyRegistry.normalize(typeCode) + ":" + raw;
    }
}
