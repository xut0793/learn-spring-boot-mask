package com.learn.mask.testsupport;

import com.learn.mask.cache.MaskResultCache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** 可观察的缓存替身：能断言有没有查、有没有写，而不必启动 Caffeine。 */
public final class CountingCache implements MaskResultCache {

    public final AtomicInteger getCount = new AtomicInteger();
    public final AtomicInteger putCount = new AtomicInteger();
    private final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();

    @Override
    public String get(String typeCode, String raw) {
        getCount.incrementAndGet();
        if (raw == null) {
            return null;
        }
        return store.get(typeCode + ":" + raw);
    }

    @Override
    public void put(String typeCode, String raw, String masked) {
        putCount.incrementAndGet();
        if (raw != null && masked != null) {
            store.put(typeCode + ":" + raw, masked);
        }
    }

    @Override
    public void invalidateAll() {
        store.clear();
    }

    public int size() {
        return store.size();
    }
}
