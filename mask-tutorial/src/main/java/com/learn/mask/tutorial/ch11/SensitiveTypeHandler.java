package com.learn.mask.tutorial.ch11;

import com.learn.mask.tutorial.ch04.MaskStrategyRegistry;
import com.learn.mask.tutorial.ch04.SensitiveType;
import com.learn.mask.tutorial.ch06.MaskEngine;
import com.learn.mask.tutorial.ch07.MaskingProperties;
import com.learn.mask.tutorial.ch09.ChannelProperties;
import com.learn.mask.tutorial.ch09.MaskingSpringBridge;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis 结果集 TypeHandler：从 ResultSet 读出字符串后按指定类型脱敏。
 * <p>
 * <b>只能在 Mapper {@code @Result(typeHandler = Xxx.class)} 里按类名引用。</b>
 * 不要注册成 Spring Bean，也不要 {@code @MappedTypes(String.class)}——
 * 那会变成全局 String 处理器，所有字符串列都按这一种类型打码。第 11.3 节有反例。
 * <p>
 * MyBatis 同样用无参构造反射创建本类，引擎从 {@link MaskingSpringBridge} 取。
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

    /** 业务自定义编码：starter 枚举里没有的类型。 */
    public SensitiveTypeHandler(String code) {
        this(SensitiveType.CUSTOM, code);
    }

    public SensitiveTypeHandler(SensitiveType type, String code) {
        this.type = type == null ? SensitiveType.CUSTOM : type;
        this.code = MaskStrategyRegistry.normalize(code);
    }

    /**
     * 写入数据库时原样透传。TypeHandler 双向工作，这里如果也打码，
     * 明文会被写成星号存进库——第 11.2 节的事故。
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
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
        ChannelProperties channels = MaskingSpringBridge.channels();
        if (raw == null || properties == null || engine == null
                || !properties.isEnabled()
                || channels == null || !channels.isMybatis()) {
            return raw;
        }
        return engine.apply(raw, type, code, MaskingSpringBridge.context());
    }
}
