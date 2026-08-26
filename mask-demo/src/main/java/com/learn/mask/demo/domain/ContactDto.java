package com.learn.mask.demo.domain;

import com.learn.mask.annotation.Sensitive;
import com.learn.mask.annotation.SensitiveType;

public class ContactDto {

    private String type;
    @Sensitive(type = SensitiveType.PHONE)
    private String value;

    public ContactDto() {
    }

    public ContactDto(String type, String value) {
        this.type = type;
        this.value = value;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
