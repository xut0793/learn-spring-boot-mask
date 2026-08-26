package com.learn.mask.demo.web;

import com.learn.mask.demo.domain.UserDto;
import com.learn.mask.jackson.SensitiveMapView;
import com.learn.mask.demo.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jackson/users")
public class JacksonUserController {

    private final UserService userService;

    public JacksonUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public UserDto get(@PathVariable Long id) {
        return userService.loadPlain(id);
    }

    @GetMapping("/{id}/as-map")
    public SensitiveMapView asMap(@PathVariable Long id) {
        return new SensitiveMapView(userService.loadAsMap(id));
    }
}
