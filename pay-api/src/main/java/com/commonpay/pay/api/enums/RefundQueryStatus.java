package com.commonpay.pay.api.enums;

/** 退款受理和到账分开表达。网络失败由异常表示，不映射成 NOT_FOUND。 */
public enum RefundQueryStatus {
    NOT_FOUND, PROCESSING, SUCCESS, CLOSED, ABNORMAL
}
