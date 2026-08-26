package com.learn.mask.web;

import com.learn.mask.cache.MaskCache;
import com.learn.mask.config.MaskRule;
import com.learn.mask.config.MaskingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 规则查询与热更新。POST 改写内存中的 {@link MaskingProperties} 并提升规则版本、清空缓存，无需重启。
 */
@RestController
@RequestMapping("/api/admin/masking")
@ConditionalOnWebApplication
public class MaskingReloadController {

    private final MaskingProperties properties;
    private final MaskCache maskCache;

    public MaskingReloadController(MaskingProperties properties, MaskCache maskCache) {
        this.properties = properties;
        this.maskCache = maskCache;
    }

    @GetMapping("/rules")
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

    @PostMapping("/reload")
    public ResponseEntity<Map<String, Object>> reload(@RequestBody(required = false) ReloadRequest request) {
        if (request != null) {
            if (request.enabled() != null) {
                properties.setEnabled(request.enabled());
            }
            if (request.channels() != null) {
                applyChannels(request.channels());
            }
            if (request.rules() != null && !request.rules().isEmpty()) {
                request.rules().forEach((key, value) -> {
                    MaskRule existing = properties.ruleOf(key);
                    if (value.getKeepPrefix() >= 0) {
                        existing.setKeepPrefix(value.getKeepPrefix());
                    }
                    if (value.getKeepSuffix() >= 0) {
                        existing.setKeepSuffix(value.getKeepSuffix());
                    }
                    existing.setMaskChar(value.getMaskChar());
                    existing.setEnabled(value.isEnabled());
                });
            }
        }
        properties.bumpRuleVersion();
        maskCache.invalidateAll();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ruleVersion", properties.getRuleVersion());
        body.put("enabled", properties.isEnabled());
        body.put("channels", properties.getChannels());
        return ResponseEntity.ok(body);
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
            Map<String, MaskRule> rules
    ) {
    }
}
