package com.learn.mask.cache;

/**
 * 明文 → 打码结果的缓存。引擎只依赖本接口，测试可用 {@link #NO_OP} 或不启动 Caffeine。
 */
public interface MaskResultCache {

    /** 未命中返回 null。 */
    String get(String typeCode, String raw);

    void put(String typeCode, String raw, String masked);

    /** 规则变更后作废全部条目。 */
    void invalidateAll();

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
