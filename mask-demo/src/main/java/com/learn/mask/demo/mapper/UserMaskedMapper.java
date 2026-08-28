package com.learn.mask.demo.mapper;

import com.learn.mask.demo.domain.UserEntity;
import com.learn.mask.demo.mask.AddressSensitiveTypeHandler;
import com.learn.mask.demo.mask.ExpressSensitiveTypeHandler;
import com.learn.mask.mybatis.BankCardSensitiveTypeHandler;
import com.learn.mask.mybatis.EmailSensitiveTypeHandler;
import com.learn.mask.mybatis.IdCardSensitiveTypeHandler;
import com.learn.mask.mybatis.PhoneSensitiveTypeHandler;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMaskedMapper {

    @Select("""
            SELECT id, name, phone, id_card, email, bank_card, city, address_detail, express_no
            FROM users
            WHERE id = #{id}
            """)
    @Results({
            @Result(column = "phone", property = "phone", typeHandler = PhoneSensitiveTypeHandler.class),
            @Result(column = "id_card", property = "idCard", typeHandler = IdCardSensitiveTypeHandler.class),
            @Result(column = "email", property = "email", typeHandler = EmailSensitiveTypeHandler.class),
            @Result(column = "bank_card", property = "bankCard", typeHandler = BankCardSensitiveTypeHandler.class),
            @Result(column = "address_detail", property = "addressDetail", typeHandler = AddressSensitiveTypeHandler.class),
            @Result(column = "express_no", property = "expressNo", typeHandler = ExpressSensitiveTypeHandler.class)
    })
    UserEntity findByIdMasked(Long id);
}
