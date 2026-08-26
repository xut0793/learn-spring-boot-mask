package com.learn.mask.demo.service;

import com.learn.mask.annotation.SensitiveMethod;
import com.learn.mask.demo.domain.AddressDto;
import com.learn.mask.demo.domain.ContactDto;
import com.learn.mask.demo.domain.UserDto;
import com.learn.mask.demo.domain.UserEntity;
import com.learn.mask.demo.mapper.ContactMapper;
import com.learn.mask.demo.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserMapper userMapper;
    private final ContactMapper contactMapper;

    public UserService(UserMapper userMapper, ContactMapper contactMapper) {
        this.userMapper = userMapper;
        this.contactMapper = contactMapper;
    }

    public UserDto loadPlain(Long id) {
        UserDto user = toDto(requireUser(id));
        log.info("loaded user phone={} idCard={} email={}", user.getPhone(), user.getIdentityCard(), user.getEmail());
        return user;
    }

    @SensitiveMethod
    public UserDto loadForAop(Long id) {
        return loadPlain(id);
    }

    public Map<String, Object> loadAsMap(Long id) {
        UserDto user = loadPlain(id);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId());
        map.put("name", user.getName());
        map.put("phone", user.getPhone());
        map.put("idCard", user.getIdentityCard());
        map.put("email", user.getEmail());
        map.put("bankCard", user.getBankCard());
        map.put("expressNo", user.getExpressNo());
        Map<String, Object> address = new LinkedHashMap<>();
        if (user.getAddress() != null) {
            address.put("city", user.getAddress().getCity());
            address.put("detail", user.getAddress().getDetail());
        }
        map.put("address", address);
        map.put("contacts", user.getContacts().stream().map(contact -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", contact.getType());
            item.put("phone", contact.getValue());
            return item;
        }).toList());
        return map;
    }

    public String fieldValue(Long id, String field) {
        UserDto user = loadPlain(id);
        return switch (field) {
            case "phone" -> user.getPhone();
            case "idCard", "id_card", "identityCard" -> user.getIdentityCard();
            case "email" -> user.getEmail();
            case "bankCard", "bank_card" -> user.getBankCard();
            case "expressNo", "express_no", "trackingNo", "tracking_no" -> user.getExpressNo();
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported field: " + field);
        };
    }

    private UserEntity requireUser(Long id) {
        UserEntity entity = userMapper.findById(id);
        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return entity;
    }

    private UserDto toDto(UserEntity entity) {
        UserDto dto = new UserDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setPhone(entity.getPhone());
        dto.setIdentityCard(entity.getIdCard());
        dto.setEmail(entity.getEmail());
        dto.setBankCard(entity.getBankCard());
        dto.setExpressNo(entity.getExpressNo());
        dto.setAddress(new AddressDto(entity.getCity(), entity.getAddressDetail()));
        List<ContactDto> contacts = contactMapper.findByUserId(entity.getId());
        dto.setContacts(contacts);
        return dto;
    }
}
