package com.commonpay.pay.api;

import com.commonpay.pay.api.dto.ClosePayRequest;
import com.commonpay.pay.api.dto.ClosePayResult;
import com.commonpay.pay.api.dto.CreatePayRequest;
import com.commonpay.pay.api.dto.CreatePayResponse;
import com.commonpay.pay.api.dto.PayNotifyCommand;
import com.commonpay.pay.api.dto.PayNotifyResult;
import com.commonpay.pay.api.dto.PayQueryRequest;
import com.commonpay.pay.api.dto.PayQueryResult;
import com.commonpay.pay.api.dto.RefundRequest;
import com.commonpay.pay.api.dto.RefundResult;

/**
 * 支付渠道 SPI：Mock / 微信 / 支付宝都实现此接口。
 */
public interface PaymentChannel {

    /** 渠道编码，如 MOCK、WECHAT */
    String channelCode();

    /** 向渠道下单，返回前端拉起支付所需信息；同一 paymentId 重试须幂等。 */
    CreatePayResponse createPay(CreatePayRequest request);

    /**
     * 解析并校验渠道回调（Mock 可简化）。
     * 验签失败应抛异常；成功返回统一结果供交易内核入账。
     */
    PayNotifyResult parseNotify(PayNotifyCommand command);

    /**
     * 关闭渠道侧的支付凭证（二维码/支付链接），须幂等。
     * <p>交易内核关单【必须】先调它 —— 只关本地不会让用户手上的二维码失效。
     * <p>返回的是渠道明确给出的业务答案；调用失败（超时/网络）请直接抛异常，
     * 让调用方知道"状态未知"，从而不关单、下轮重试。
     * <p>默认抛异常：未实现的渠道意味着它的单【无法安全关闭】，别静默放行。
     */
    default ClosePayResult closePay(ClosePayRequest request) {
        throw new UnsupportedOperationException(channelCode() + " 渠道未实现关单，无法安全关闭该渠道的订单");
    }

    /**
     * 主动查这笔单在渠道的最终状态。
     * <p>两个场景都要用它：
     * ① 关单时渠道说「已支付」—— 本地缺通知号，查一次把账入进去；
     * ② 回调迟迟不来 —— 定期查一次，别让订单永远挂着。
     * <p>调用失败请抛异常，不要返回 {@code NOT_PAID} —— 把"不知道"说成"没付"会误关订单。
     */
    default PayQueryResult queryPay(PayQueryRequest request) {
        throw new UnsupportedOperationException(channelCode() + " 渠道未实现查单，无法确认支付结果");
    }

    /** 稳定 refundNo 幂等退款；受理不等于退款到账。 */
    default RefundResult refund(RefundRequest request) {
        throw new UnsupportedOperationException(channelCode() + " 渠道未实现退款");
    }

    /** 不确定的调用结果必须先查；查询异常不能当成退款不存在。 */
    default RefundResult queryRefund(RefundRequest request) {
        throw new UnsupportedOperationException(channelCode() + " 渠道未实现退款查询");
    }

    default RefundResult parseRefundNotify(PayNotifyCommand command) {
        throw new UnsupportedOperationException(channelCode() + " 渠道未实现退款回调");
    }
}
