package com.learn.mask.demo.domain;

import com.learn.mask.annotation.Sensitive;
import com.learn.mask.annotation.SensitiveType;

public class AddressDto {

    private String city;
    /**
     * 详细地址走业务自定义策略 {@code AddressMaskStrategy}（门牌号打星）。
     */
    @Sensitive(type = SensitiveType.ADDRESS)
    private String detail;

    public AddressDto() {
    }

    public AddressDto(String city, String detail) {
        this.city = city;
        this.detail = detail;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
