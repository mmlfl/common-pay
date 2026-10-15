package com.commonpay.trade.dto;

import com.commonpay.trade.enums.CreateOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 下单结果。异步场景看 {@link CreateOrderStatus#ACCEPTED} + {@code requestId}；
 * 同步场景看 {@link CreateOrderStatus#OK} + {@code orderNo}。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateOrderResult {

    private CreateOrderStatus status;

    /** 仅 {@link CreateOrderStatus#OK}（同步落库完成）时有值。 */
    private String orderNo;

    /**
     * 异步受理号：扣库存成功后生成，随 MQ 消息传递；
     * 消费者落库后可用它关联 / 幂等；客户端凭它查「是否已生成订单」。
     */
    private String requestId;

    public static CreateOrderResult ok(String orderNo) {
        CreateOrderResult r = new CreateOrderResult();
        r.setStatus(CreateOrderStatus.OK);
        r.setOrderNo(orderNo);
        return r;
    }

    /** 异步受理成功。 */
    public static CreateOrderResult accepted(String requestId) {
        CreateOrderResult r = new CreateOrderResult();
        r.setStatus(CreateOrderStatus.ACCEPTED);
        r.setRequestId(requestId);
        return r;
    }

    public static CreateOrderResult processing(String requestId) {
        CreateOrderResult r = new CreateOrderResult();
        r.setStatus(CreateOrderStatus.PROCESSING);
        r.setRequestId(requestId);
        return r;
    }

    /** 同步成功或异步已受理，都算「调用成功」。 */
    public boolean isSuccess() {
        return status == CreateOrderStatus.OK || status == CreateOrderStatus.ACCEPTED;
    }
}
