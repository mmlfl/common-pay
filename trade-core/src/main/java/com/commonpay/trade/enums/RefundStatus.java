package com.commonpay.trade.enums;

/** PENDING 含请求结果未知；必须先查，再用原退款号重试。 */
public enum RefundStatus {
    PENDING(0), PROCESSING(1), SUCCESS(2), MANUAL(3);
    private final int code;
    RefundStatus(int code) { this.code = code; }
    public int getCode() { return code; }
    public static RefundStatus of(int code) {
        for (RefundStatus status : values()) if (status.code == code) return status;
        throw new IllegalArgumentException("未知退款状态: " + code);
    }
}
