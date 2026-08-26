package com.learn.mask.tutorial.ch06;

/**
 * 明文 → 打码结果的缓存。
 * <p>
 * 本章只定义接口并用 {@link #NO_OP} 接上，真正的 Caffeine 实现在第 8 章。
 * 提前把位置留出来，是为了让引擎的判定链**写一次就定型**——
 * 第 8 章加缓存不需要回来改 {@link MaskEngine}。
 */
public interface MaskResultCache {

    /** 未命中返回 null。 */
    String get(String typeCode, String raw);

    void put(String typeCode, String raw, String masked);

    /** 规则变更后作废全部条目，第 7 章热更新会用到。 */
    void invalidateAll();

    /** 什么都不做的实现，等价于「不开缓存」。 */
    MaskResultCache NO_OP = new MaskResultCache() {
        @Override
        public String get(String typeCode, String raw) {
            return null;
        }

        @Override
        public void put(String typeCode, String raw, String masked) {
        }

        @Override
        public void invalidateAll() {
        }
    };
}
