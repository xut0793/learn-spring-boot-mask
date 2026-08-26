package com.learn.mask.demo.mapper;

import com.learn.mask.demo.domain.ContactDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ContactMapper {

    @Select("SELECT type, contact_value FROM contacts WHERE user_id = #{userId} ORDER BY id")
    @Results({
            @Result(column = "contact_value", property = "value")
    })
    List<ContactDto> findByUserId(Long userId);
}
