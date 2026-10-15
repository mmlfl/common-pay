package com.commonpay.pay.api.enums;

/**
 * 渠道查单的业务结果。
 * <p>和关单一样：只表达渠道明确给出的答案；调用失败（超时/网络/渠道不支持）用抛异常表达，
 * 让调用方知道"状态未知"，不要拿未知当未支付。
 */
public enum PayQueryStatus {

    /** 渠道确认这笔已支付 */
    PAID,

    /** 渠道确认未支付（含已关闭、已撤销、支付失败） */
    NOT_PAID,

    /** 渠道查无此单 */
    NOT_FOUND,
    /** 渠道明确关闭，无法继续付款；NOT_PAID 只表示这一刻还没付款。 */
    CLOSED
}
