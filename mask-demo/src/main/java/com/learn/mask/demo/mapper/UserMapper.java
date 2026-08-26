package com.learn.mask.demo.mapper;

import com.learn.mask.demo.domain.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    @Select("""
            SELECT id, name, phone, id_card, email, bank_card, city, address_detail, express_no
            FROM users
            WHERE id = #{id}
            """)
    @Results({
            @Result(column = "id_card", property = "idCard"),
            @Result(column = "bank_card", property = "bankCard"),
            @Result(column = "address_detail", property = "addressDetail"),
            @Result(column = "express_no", property = "expressNo")
    })
    UserEntity findById(Long id);
}
