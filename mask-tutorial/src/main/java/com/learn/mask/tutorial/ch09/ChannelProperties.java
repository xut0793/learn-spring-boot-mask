package com.learn.mask.tutorial.ch09;

/**
 * 四个通道的独立开关。第 9 章只用到 {@code jackson}，其余三个在第 10~12 章接入，
 * 第 13 章讨论它们为什么不能随便组合。
 * <p>
 * 默认值和 {@code mask-starter} 一致：出口通道（Jackson / Logback）默认开，
 * 突变通道（AOP / MyBatis）默认关——因为突变通道会改内存对象，误开的代价更大。
 */
public class ChannelProperties {

    private boolean jackson = true;
    private boolean logback = true;
    private boolean aop = false;
    private boolean mybatis = false;
    /**
     * 第 13 章：Jackson 与突变通道同时开启时，true 则启动失败，false 只打 WARN。
     */
    private boolean strict = false;

    public boolean isJackson() {
        return jackson;
    }

    public void setJackson(boolean jackson) {
        this.jackson = jackson;
    }

    public boolean isLogback() {
        return logback;
    }

    public void setLogback(boolean logback) {
        this.logback = logback;
    }

    public boolean isAop() {
        return aop;
    }

    public void setAop(boolean aop) {
        this.aop = aop;
    }

    public boolean isMybatis() {
        return mybatis;
    }

    public void setMybatis(boolean mybatis) {
        this.mybatis = mybatis;
    }

    public boolean isStrict() {
        return strict;
    }

    public void setStrict(boolean strict) {
        this.strict = strict;
    }
}
