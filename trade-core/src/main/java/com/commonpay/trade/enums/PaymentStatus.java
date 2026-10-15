package com.commonpay.trade.enums;

/**
 * 支付单状态，对应 trade_payment.status。
 */
public enum PaymentStatus {

    INIT(0, "已创建"),
    WAITING(1, "待支付"),
    SUCCESS(2, "成功"),
    CLOSED(3, "关闭");

    private final int code;
    private final String desc;

    PaymentStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static PaymentStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (PaymentStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知支付单状态: " + code);
    }
}
