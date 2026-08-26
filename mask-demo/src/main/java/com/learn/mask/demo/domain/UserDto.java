package com.learn.mask.demo.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.learn.mask.annotation.Sensitive;
import com.learn.mask.annotation.SensitiveType;
import com.learn.mask.demo.mask.DemoSensitiveTypes;

import java.util.ArrayList;
import java.util.List;

public class UserDto {

    private Long id;
    private String name;
    @Sensitive(type = SensitiveType.PHONE, reversible = true)
    private String phone;
    @Sensitive(type = SensitiveType.ID_CARD, reversible = true)
    private String identityCard;
    @Sensitive(type = SensitiveType.EMAIL)
    private String email;
    @Sensitive(type = SensitiveType.BANK_CARD)
    private String bankCard;
    /**
     * 快递单号：starter 未预定义该类型，使用业务编码 {@link com.learn.mask.demo.mask.DemoSensitiveTypes#EXPRESS}。
     */
    @Sensitive(code = DemoSensitiveTypes.EXPRESS)
    private String expressNo;
    private AddressDto address;
    private List<ContactDto> contacts = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    @JsonProperty("idCard")
    @Sensitive(type = SensitiveType.ID_CARD, reversible = true)
    public String getIdentityCard() {
        return identityCard;
    }

    public void setIdentityCard(String identityCard) {
        this.identityCard = identityCard;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getBankCard() {
        return bankCard;
    }

    public void setBankCard(String bankCard) {
        this.bankCard = bankCard;
    }

    public String getExpressNo() {
        return expressNo;
    }

    public void setExpressNo(String expressNo) {
        this.expressNo = expressNo;
    }

    public AddressDto getAddress() {
        return address;
    }

    public void setAddress(AddressDto address) {
        this.address = address;
    }

    public List<ContactDto> getContacts() {
        return contacts;
    }

    public void setContacts(List<ContactDto> contacts) {
        this.contacts = contacts;
    }
}
