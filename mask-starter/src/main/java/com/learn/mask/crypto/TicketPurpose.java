package com.learn.mask.crypto;

/**
 * 票据用途。VIEW 给客服弹层刷新；DIAL 给外呼系统核销。
 */
public enum TicketPurpose {
    /** 页面查看明文，可 refresh 同一令牌。 */
    VIEW,
    /** 外呼核销，换取明文号码。 */
    DIAL
}
