package com.learn.mask.tutorial.ch14;

/**
 * 票据用途。VIEW 给客服弹层刷新；DIAL 给外呼系统核销。
 * 两种票不能混用：看见号码的票不能拿去拨号，拨号票也不能拿去刷新页面。
 */
public enum TicketPurpose {
    VIEW,
    DIAL
}
