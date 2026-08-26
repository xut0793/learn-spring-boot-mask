package com.learn.mask.tutorial.ch06;

import java.util.HashMap;
import java.util.Map;

/**
 * 测试用的极简缓存：统计读写次数，好验证引擎在什么时候查了缓存、什么时候没查。
 * 真正的 Caffeine 实现在第 8 章。
 */
class CountingCache implements MaskResultCache {

    private final Map<String, String> store = new HashMap<>();
    int getCount;
    int putCount;

    @Override
    public String get(String typeCode, String raw) {
        getCount++;
        return store.get(typeCode + ":" + raw);
    }

    @Override
    public void put(String typeCode, String raw, String masked) {
        putCount++;
        store.put(typeCode + ":" + raw, masked);
    }

    @Override
    public void invalidateAll() {
        store.clear();
    }

    int size() {
        return store.size();
    }
}
