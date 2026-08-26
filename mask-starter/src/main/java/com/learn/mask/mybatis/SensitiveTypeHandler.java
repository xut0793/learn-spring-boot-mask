package com.learn.mask.mybatis;

import com.learn.mask.annotation.SensitiveType;
import com.learn.mask.config.MaskingProperties;
import com.learn.mask.engine.MaskEngine;
import com.learn.mask.support.MaskingSpringBridge;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis 结果集 TypeHandler：读出字符串后按指定类型脱敏。
 * 只能在 Mapper {@code @Result(typeHandler = Xxx.class)} 中按类名引用，不要注册成 Spring Bean。
 * 业务自定义类型使用 {@link #SensitiveTypeHandler(String)} 传入编码即可。
 */
public class SensitiveTypeHandler extends BaseTypeHandler<String> {

    private final SensitiveType type;
    private final String code;

    public SensitiveTypeHandler() {
        this(SensitiveType.CUSTOM);
    }

    public SensitiveTypeHandler(SensitiveType type) {
        this(type, type == null ? SensitiveType.CUSTOM.name() : type.name());
    }

    /**
     * 业务自定义类型：starter 枚举中不存在的编码。
     */
    public SensitiveTypeHandler(String code) {
        this(SensitiveType.CUSTOM, code);
    }

    public SensitiveTypeHandler(SensitiveType type, String code) {
        this.type = type == null ? SensitiveType.CUSTOM : type;
        this.code = MaskingProperties.normalizeCode(code, this.type);
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return mask(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return mask(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return mask(cs.getString(columnIndex));
    }

    private String mask(String raw) {
        MaskingProperties properties = MaskingSpringBridge.properties();
        MaskEngine engine = MaskingSpringBridge.engine();
        if (raw == null || properties == null || engine == null || !properties.isEnabled() || !properties.getChannels().isMybatis()) {
            return raw;
        }
        return engine.apply(raw, type, code, MaskingSpringBridge.context());
    }
}
