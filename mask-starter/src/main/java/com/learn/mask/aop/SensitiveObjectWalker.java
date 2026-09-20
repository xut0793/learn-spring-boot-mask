package com.learn.mask.aop;

import com.learn.mask.annotation.Sensitive;
import com.learn.mask.config.MaskingProperties;
import com.learn.mask.context.MaskContext;
import com.learn.mask.engine.MaskEngine;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 递归遍历返回对象，对带 {@link Sensitive} 的字符串字段就地打星。会改写内存中的原对象。
 */
public class SensitiveObjectWalker {

    /** JDK / Jackson 等类型不再向下反射，避免扫完整对象图。 */
    private static final Set<String> SKIP_PACKAGES = Set.of("java.", "javax.", "jakarta.", "tools.jackson.", "com.fasterxml.");

    private final MaskEngine engine;
    private final MaskingProperties properties;
    private final MaskContext maskContext;

    public SensitiveObjectWalker(MaskEngine engine, MaskingProperties properties, MaskContext maskContext) {
        this.engine = engine;
        this.properties = properties;
        this.maskContext = maskContext;
    }

    /** 就地改写 {@code target} 及其嵌套结构中的敏感字符串，返回同一引用。 */
    public Object mask(Object target) {
        walk(target, new IdentityHashMap<>());
        return target;
    }

    private void walk(Object target, IdentityHashMap<Object, Boolean> seen) {
        if (target == null || seen.containsKey(target)) {
            return;
        }
        if (target instanceof Collection<?> collection) {
            seen.put(target, Boolean.TRUE);
            for (Object item : collection) {
                walk(item, seen);
            }
            return;
        }
        if (target.getClass().isArray()) {
            seen.put(target, Boolean.TRUE);
            int length = Array.getLength(target);
            for (int i = 0; i < length; i++) {
                walk(Array.get(target, i), seen);
            }
            return;
        }
        if (target instanceof Map<?, ?> map) {
            seen.put(target, Boolean.TRUE);
            maskMap(map);
            for (Object value : map.values()) {
                walk(value, seen);
            }
            return;
        }
        if (shouldSkip(target.getClass())) {
            return;
        }
        seen.put(target, Boolean.TRUE);
        maskFields(target, seen);
    }

    @SuppressWarnings("unchecked")
    private void maskMap(Map<?, ?> map) {
        if (!(map instanceof Map<?, ?> raw)) {
            return;
        }
        Map<Object, Object> writable = (Map<Object, Object>) raw;
        for (Map.Entry<Object, Object> entry : writable.entrySet()) {
            if (entry.getKey() instanceof String key && entry.getValue() instanceof String value) {
                String typeCode = properties.typeCodeOf(key);
                if (typeCode != null) {
                    entry.setValue(engine.apply(value, null, typeCode, maskContext));
                }
            }
        }
    }

    private void maskFields(Object target, IdentityHashMap<Object, Boolean> seen) {
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                field.setAccessible(true);
                try {
                    Object value = field.get(target);
                    Sensitive sensitive = field.getAnnotation(Sensitive.class);
                    if (sensitive != null && value instanceof String text) {
                        field.set(target, engine.apply(text, sensitive.type(), sensitive.code(), maskContext));
                    } else {
                        walk(value, seen);
                    }
                } catch (IllegalAccessException ignored) {
                    // skip inaccessible field
                }
            }
            type = type.getSuperclass();
        }
    }

    private boolean shouldSkip(Class<?> type) {
        if (type.isPrimitive() || type.isEnum() || type == String.class) {
            return true;
        }
        String name = type.getName();
        for (String prefix : SKIP_PACKAGES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
