package com.learn.mask.demo.web;

import com.learn.mask.demo.domain.UserEntity;
import com.learn.mask.demo.mapper.UserMaskedMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/db/users")
public class DbUserController {

    private final UserMaskedMapper userMaskedMapper;

    public DbUserController(UserMaskedMapper userMaskedMapper) {
        this.userMaskedMapper = userMaskedMapper;
    }

    @GetMapping("/{id}")
    public UserEntity get(@PathVariable Long id) {
        UserEntity entity = userMaskedMapper.findByIdMasked(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return entity;
    }
}
