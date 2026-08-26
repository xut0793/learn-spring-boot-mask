package com.learn.mask.demo.web;

import com.learn.mask.context.MaskContext;
import com.learn.mask.crypto.ReversibleMasker;
import com.learn.mask.demo.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class UnmaskController {

    private final UserService userService;
    private final MaskContext maskContext;
    private final ReversibleMasker reversibleMasker;

    public UnmaskController(UserService userService, MaskContext maskContext, ReversibleMasker reversibleMasker) {
        this.userService = userService;
        this.maskContext = maskContext;
        this.reversibleMasker = reversibleMasker;
    }

    @PostMapping("/api/unmask")
    public Map<String, Object> unmask(@RequestBody UnmaskRequest request) {
        if (!maskContext.canUnmask()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Current role cannot unmask");
        }
        String plain = userService.fieldValue(request.userId(), request.field());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", request.userId());
        body.put("field", request.field());
        body.put("value", plain);
        body.put("token", reversibleMasker.encrypt(plain));
        return body;
    }

    public record UnmaskRequest(Long userId, String field) {
    }
}
