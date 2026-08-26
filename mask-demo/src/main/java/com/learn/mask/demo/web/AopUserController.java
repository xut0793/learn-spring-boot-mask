package com.learn.mask.demo.web;

import com.learn.mask.demo.domain.UserDto;
import com.learn.mask.demo.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/aop/users")
public class AopUserController {

    private final UserService userService;

    public AopUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{id}")
    public UserDto get(@PathVariable Long id) {
        return userService.loadForAop(id);
    }
}
