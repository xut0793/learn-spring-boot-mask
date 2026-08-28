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

    public MaskingReloadController(MaskingProperties properties, MaskResultCache maskCache) {
        this(new MaskingReloadService(properties, maskCache));
    }

    public MaskingReloadController(MaskingReloadService reloadService) {
        this.reloadService = reloadService;
    }

    @GetMapping("/rules")
    public Map<String, Object> current() {
        return reloadService.current();
    }

    @PostMapping("/reload")
    public ResponseEntity<Map<String, Object>> reload(@RequestBody(required = false) MaskingReloadService.ReloadRequest request) {
        return ResponseEntity.ok(reloadService.reload(request));
    }
}
