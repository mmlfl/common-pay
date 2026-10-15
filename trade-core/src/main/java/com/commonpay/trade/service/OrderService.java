package com.commonpay.trade.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.commonpay.trade.dto.CreateOrderResult;
import com.commonpay.trade.dto.OrderProgress;
import com.commonpay.trade.entity.Order;
import com.commonpay.trade.dto.CreateOrderRequest;
import com.commonpay.trade.mq.common.CreateOrderMessage;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单表 CRUD + 下单/关单业务。
 * 跨表只依赖其他 Service，不直接注入别的表 Mapper。
 */
public interface OrderService extends IService<Order> {

    Order createOrder(CreateOrderRequest command);

    Order getOrder(Long orderId);

    /** 在事务中锁住订单，用于支付和关单状态竞争。 */
    Order getOrderForUpdate(Long orderId);

    boolean updateStatus(Long orderId, int fromStatus, int toStatus);

    /**
     * 推后支付截止时间。只给「首次创建支付单」用 —— 让用户点支付之后总有完整的支付窗口。
     * <p>调用方必须已经持有订单行锁（{@link #getOrderForUpdate}），否则会与关单打架。
     */
    boolean extendPayDeadline(Long orderId, LocalDateTime deadline);

    List<Order> listExpiredWaitPay(LocalDateTime deadline, int limit);

    /** 扫描并关闭超时未支付订单，归还库存（定时兜底） */
    int closeExpiredOrders(int batchSize);

    /**
     * 尝试关闭一单：仅当仍为待支付且 {@code payDeadline <= now}。
     * 延迟 MQ 与定时任务共用；已支付 / 已取消 / 被延长未到期 → false。
     */
    boolean tryCloseIfExpired(Long orderId);

    /** Redis 预扣 + Outbox 受理；预扣失败抛异常，不降级到同步 DB 下单。 */
    CreateOrderResult createOrderRedis(CreateOrderRequest command);

    CreateOrderResult createOrderFromMq(CreateOrderMessage message);

    /** 查异步下单进度：PENDING / CREATED(+orderId) / FAILED */
    OrderProgress getCreateProgress(String requestId);

    void giveUp(CreateOrderMessage message, String reason);
}
