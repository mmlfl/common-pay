package com.commonpay.trade.enums;

/**
 * 商品上下架，对应 trade_product.status。
 */
public enum ProductStatus {

    OFF(0, "下架"),
    ON(1, "上架");

    private final int code;
    private final String desc;

    ProductStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static ProductStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (ProductStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知商品状态: " + code);
    }
}
