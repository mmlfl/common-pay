package com.commonpay.trade.enums;

import java.util.Optional;

/**
 * 订单状态，对应 trade_order.status。
 */
public enum OrderStatus {

    WAIT_PAY(0, "待支付"),
    PAID(1, "已支付"),
    CANCELLED(2, "已取消");

    private final int code;
    private final String desc;

    OrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static Optional<OrderStatus> of(Integer code) {
        if (code == null) {
            return Optional.empty();
        }
        for (OrderStatus status : values()) {
            if (status.code == code) {
                return Optional.of(status);
            }
        }
        return  Optional.empty();
    }
}
