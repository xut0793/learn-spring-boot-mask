package com.learn.mask.web;

import com.learn.mask.cache.MaskResultCache;
import com.learn.mask.config.MaskingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 规则查询与热更新，委托 {@link MaskingReloadService}。
 */
@RestController
@RequestMapping("/api/admin/masking")
@ConditionalOnWebApplication
public class MaskingReloadController {

    private final MaskingReloadService reloadService;

    /** 便于 Spring 注入 properties 与 cache 的便捷构造。 */
    public MaskingReloadController(MaskingProperties properties, MaskResultCache maskCache) {
        this(new MaskingReloadService(properties, maskCache));
    }

    public MaskingReloadController(MaskingReloadService reloadService) {
        this.reloadService = reloadService;
    }

    /** GET {@code /api/admin/masking/rules}。 */
    @GetMapping("/rules")
    public Map<String, Object> current() {
        return reloadService.current();
    }

    /** POST {@code /api/admin/masking/reload}，body 可省略表示仅 bump 快照/版本。 */
    @PostMapping("/reload")
    public ResponseEntity<Map<String, Object>> reload(@RequestBody(required = false) MaskingReloadService.ReloadRequest request) {
        return ResponseEntity.ok(reloadService.reload(request));
    }
}
