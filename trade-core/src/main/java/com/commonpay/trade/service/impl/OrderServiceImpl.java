package com.commonpay.trade.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.commonpay.trade.dto.CreateOrderResult;
import com.commonpay.trade.dto.OrderProgress;
import com.commonpay.trade.entity.Inventory;
import com.commonpay.trade.entity.Order;
import com.commonpay.trade.entity.Payment;
import com.commonpay.trade.entity.Outbox;
import com.commonpay.trade.entity.Product;
import com.commonpay.trade.common.ErrorCode;
import com.commonpay.trade.exception.BaseException;
import com.commonpay.trade.exception.BusinessException;
import com.commonpay.trade.enums.OrderStatus;
import com.commonpay.trade.enums.OutboxStatus;
import com.commonpay.trade.enums.PaymentStatus;
import com.commonpay.trade.enums.ProductStatus;
import com.commonpay.trade.mapper.OrderMapper;
import com.commonpay.trade.mq.common.CreateOrderMessage;
import com.commonpay.trade.mq.common.DelayCloseOrderMessage;
import com.commonpay.trade.redis.DecrResult;
import com.commonpay.trade.redis.ProductBloomGate;
import com.commonpay.trade.redis.RedisKeys;
import com.commonpay.trade.redis.RedisUtil;
import com.commonpay.pay.api.PaymentChannel;
import com.commonpay.pay.api.dto.ClosePayRequest;
import com.commonpay.pay.api.dto.ClosePayResult;
import com.commonpay.pay.api.enums.ClosePayStatus;
import com.commonpay.pay.api.enums.PayQueryStatus;
import com.commonpay.trade.service.InventoryService;
import com.commonpay.trade.service.OrderCloseService;
import com.commonpay.trade.service.OrderService;
import com.commonpay.trade.service.OutboxService;
import com.commonpay.trade.service.PaymentChannelFactory;
import com.commonpay.trade.service.PaymentReconcileService;
import com.commonpay.trade.service.PaymentService;
import com.commonpay.trade.service.ProductService;
import com.commonpay.trade.config.OrderCloseExecutorConfig;
import com.commonpay.trade.dto.CreateOrderRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.ObjectUtils;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;


/**
 * 下单 / 关单：只注入 Product / Inventory / Payment 的 Service。
 * 本表通过继承 ServiceImpl 获得 CRUD。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    private final OrderMapper orderMapper;
    private final ProductBloomGate productBloomGate;
    private final RedissonClient redissonClient;

    private final ProductService productService;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;

    private final RedisUtil redisUtil;
    private final OutboxService outboxService;
    private final OrderIdempotencyService idempotencyService;
    private final OrderAcceptanceService acceptanceService;
    private final ReservationDebtService reservationDebtService;
    private final PaymentChannelFactory channelFactory;
    private final OrderCloseService orderCloseService;
    private final PaymentReconcileService paymentReconcileService;
    private final RocketMQTemplate rocketMQTemplate;

    /** Bean 名 orderCloseExecutor，与其它 ExecutorService 区分 */
    @Resource(name = OrderCloseExecutorConfig.ORDER_CLOSE_EXECUTOR)
    private ExecutorService orderCloseExecutor;

    /**
     * 时间片宽度。小商户场景默认 1 分钟：片更细、单片命中少，和调度 1 分钟对齐即可。
     * 达不到「1 分钟内数千单超时」的量级，不必为极限吞吐把片拉大。
     */
    @Value("${trade.order-close.slice-minutes:1}")
    private int sliceMinutes;
    /**
     * 每轮只扫最近多少个时间片（默认 3 = 最近约 3 分钟）。
     * 刚到期主要靠延迟 MQ；定时兜漏网，每片每轮最多一批。
     */
    @Value("${trade.order-close.max-slices-per-tick:3}")
    private int maxSlicesPerTick;
    /** 时间片 / 重试队列每批条数；每个时间片每轮最多捞一批 */
    @Value("${trade.order-close.batch-size:100}")
    private int closeBatchSize;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order createOrder(CreateOrderRequest command) {

        Long productId = command.getProductId();
        Product product = productService.getById(productId);
        if (ObjectUtils.isEmpty(product)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_EXISTS);
        }
        Inventory inventory = inventoryService.getByProductId(productId);
        if (ObjectUtils.isEmpty(inventory) || Objects.isNull(inventory.getCnt())) {
            throw new BusinessException(ErrorCode.STOCK_NOT_FOUND);
        }
        boolean b = inventoryService.decreaseStock(command.getProductId(), command.getQuantity());
        if (!b) {
            throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT);
        }

        BigDecimal amount = product.getPrice().multiply(BigDecimal.valueOf(command.getQuantity()));
        Order order = Order.builder()
                .amount(amount)
                .productId(command.getProductId())
                .quantity(command.getQuantity())
                .status(OrderStatus.WAIT_PAY.getCode())
                .userId(command.getUserId())
                .requestId(command.getRequestId())
                .payDeadline(LocalDateTime.now().plusMinutes(30))
                .build();
        save(order);

        return order;
    }

    /**
     * 异步下单：成功返回受理号，业务拒绝抛异常；幂等保存交给独立服务。
     */
    @Override
    public CreateOrderResult createOrderRedis(CreateOrderRequest command) {
        OrderIdempotencyService.Claim claim = idempotencyService.claim(command);
        if (!claim.owner()) {
            if (idempotencyService.isRejected(claim)) idempotencyService.replayFailure(claim);
            if (idempotencyService.isAccepted(claim)) return CreateOrderResult.accepted(command.getRequestId());
            // 只查已有受理事实，不会降级扣 MySQL 库存。也修复受理成功但缓存写回失败的情况。
            if (wasAccepted(command)) {
                idempotencyService.accepted(claim);
                return CreateOrderResult.accepted(command.getRequestId());
            }
            idempotencyService.replayFailure(claim);
            return CreateOrderResult.processing(command.getRequestId());
        }
        try {
            // 新请求先过 Bloom，不存在的商品不会触发下面的 DB 受理记录查询。
            if (productBloomGate.definitelyAbsent(command.getProductId())) {
                throw new BusinessException(ErrorCode.PRODUCT_NOT_EXISTS);
            }
            // 终态缓存过期后，先查持久化幂等记录，不能再次预扣库存。
            if (!wasAccepted(command)) doCreateOrderRedis(command, claim);
            idempotencyService.accepted(claim);
            return CreateOrderResult.accepted(command.getRequestId());
        } catch (BusinessException e) {
            idempotencyService.rejected(claim, e);
            throw e;
        } catch (RuntimeException e) {
            // Redis 超时或数据库提交异常可能发生在副作用之后，不盲目删占位/归还库存。
            idempotencyService.unknown(claim);
            log.error("下单结果未知，保留预扣与原受理号 requestId={}", command.getRequestId(), e);
            if (e instanceof BaseException) throw e;
            throw new BaseException(ErrorCode.ORDER_RESULT_UNKNOWN, e);
        }
    }

    private void doCreateOrderRedis(CreateOrderRequest command, OrderIdempotencyService.Claim claim) {
        String key = RedisKeys.stock(command.getProductId());
        if (!redisUtil.hasKey(key)) warmUpStockFromDb(command.getProductId());

        String attemptToken = claim.snapshot().getToken();
        // 预扣 + 登记崩溃对账义务（同一次 Lua），缩小「扣了库存却没义务」窗口
        DecrResult decr = redisUtil.decrementIfEnoughWithDebt(
                command.getProductId(),
                command.getQuantity(),
                command.getRequestId(),
                attemptToken,
                ReservationDebtService.DEBT_TTL);
        if (!decr.isSuccess()) {
            switch (decr.getErrorCode()) {
                case KEY_MISSING -> throw new BusinessException(ErrorCode.STOCK_NOT_READY);
                case INSUFFICIENT -> throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT);
                case UNKNOWN -> throw new BaseException(ErrorCode.ORDER_RESULT_UNKNOWN);
            }
        }
        CreateOrderMessage message = new CreateOrderMessage(command.getRequestId(), command.getUserId(),
                command.getProductId(), command.getQuantity(), attemptToken);
        try {
            acceptanceService.accept(message);
            reservationDebtService.clear(command.getRequestId());
        } catch (OrderAcceptanceService.NotAcceptedException e) {
            // 仅确认无 Outbox 时才归还预扣；否则可能已受理，归还会超卖
            if (acceptanceService.existsByRequestId(command.getRequestId())) {
                throw new BaseException(ErrorCode.ORDER_RESULT_UNKNOWN, e);
            }
            try {
                redisUtil.returnReservationOnce(command.getProductId(), command.getQuantity(), attemptToken);
                reservationDebtService.clear(command.getRequestId());
            } catch (RuntimeException compensationError) {
                // 即时回补失败：保留义务，交给 ReservationDebtJob
                compensationError.addSuppressed(e);
                throw new BaseException(ErrorCode.ORDER_RESULT_UNKNOWN, compensationError);
            }
            if (e.getCause() instanceof org.springframework.dao.DuplicateKeyException && wasAccepted(command)) return;
            throw new BusinessException(ErrorCode.ORDER_ACCEPT_FAILED, e);
        }
        // 其它事务异常（提交/回滚结果未知）直接传播，保留预扣与义务等待对账。
    }

    private boolean wasAccepted(CreateOrderRequest command) {
        Order existing = orderMapper.getByRequestId(command.getRequestId());
        if (existing != null) {
            checkSameRequest(command, existing.getUserId(), existing.getProductId(), existing.getQuantity());
            return true;
        }
        Outbox record = outboxService.lambdaQuery().eq(Outbox::getRequestId, command.getRequestId()).one();
        if (record == null) return false;
        CreateOrderMessage saved = JSON.parseObject(record.getPayload(), CreateOrderMessage.class);
        if (saved == null) throw new BaseException(ErrorCode.ORDER_RESULT_UNKNOWN);
        checkSameRequest(command, saved.getUserId(), saved.getProductId(), saved.getQuantity());
        if (Objects.equals(record.getStatus(), OutboxStatus.FAILED.getCode())
                || Objects.equals(record.getStatus(), OutboxStatus.COMPENSATED.getCode())) {
            throw new BusinessException(ErrorCode.ORDER_REJECTED);
        }
        return true;
    }

    private void checkSameRequest(CreateOrderRequest request, String userId, Long productId, Integer quantity) {
        if (!Objects.equals(request.getUserId(), userId) || !Objects.equals(request.getProductId(), productId)
                || !Objects.equals(request.getQuantity(), quantity)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
    }

    /**
     * 冷路径只预热；拒绝时抛业务异常，不构造下单返回值。
     */
    private void warmUpStockFromDb(Long productId) {
        String key = RedisKeys.stock(productId);
        RLock lock = redissonClient.getLock(redisUtil.fullKey(RedisKeys.stockWarmupLock(productId)));
        lock.lock();
        try {
            if (redisUtil.hasKey(key)) return;
            Product product = productService.getById(productId);
            if (product == null) throw new BusinessException(ErrorCode.PRODUCT_NOT_EXISTS);
            if (!Objects.equals(product.getStatus(), ProductStatus.ON.getCode())) {
                throw new BusinessException(ErrorCode.PRODUCT_OFF_SHELF);
            }
            // 保留回库序号一起预热，避免取消回库任务再次增加快照已经包含的库存。
            if (!inventoryService.warmUpStock(productId)) throw new BusinessException(ErrorCode.STOCK_NOT_FOUND);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public OrderProgress getCreateProgress(String requestId) {
        // ① 订单已创建
        Order order = orderMapper.getByRequestId(requestId);
        if (order != null) {
            return OrderProgress.created(order.getOrderId());
        }
        // ② 没有订单，看消息是不是已被判定失败 —— 那样这单永远建不出来了。
        //    等 FAILED 就够了，不必等补偿跑完：补偿只影响库存，不影响这单的结局
        Outbox outbox = outboxService.lambdaQuery()
                .eq(Outbox::getRequestId, requestId)
                .one();
        if (outbox != null
                && (Objects.equals(outbox.getStatus(), OutboxStatus.FAILED.getCode())
                || Objects.equals(outbox.getStatus(), OutboxStatus.COMPENSATED.getCode()))) {
            return OrderProgress.failed();
        }
        if (outbox != null) return OrderProgress.pending();
        if (idempotencyService.isFailed(requestId)) return OrderProgress.failed();
        // 未确认受理的请求也保持 PENDING，不能据此宣布受理成功或失败。
        return OrderProgress.pending();
    }

    /**
     * 消费端判定业务失败 → 标 FAILED 并 ACK。
     * <p>这里只改 Outbox 状态（停掉 OutboxSendJob 的重发），Redis 预扣由
     * {@link com.commonpay.trade.job.OutboxCompensateJob} 扫 FAILED 后归还。
     * PENDING/SENT 条件更新保证重复 giveUp 幂等，不会重复触发补偿义务之外的状态抖动。
     */
    @Override
    public void giveUp(CreateOrderMessage message, String reason) {
        outboxService.lambdaUpdate()
                .eq(Outbox::getRequestId, message.getRequestId())
                .in(Outbox::getStatus, OutboxStatus.PENDING.getCode(), OutboxStatus.SENT.getCode())
                .set(Outbox::getStatus, OutboxStatus.FAILED.getCode())
                .update();
        idempotencyService.markGiveUp(message.getRequestId(), reason);
        log.warn("消费端判定业务失败,已标FAILED requestId={} reason={}", message.getRequestId(), reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateOrderResult createOrderFromMq(CreateOrderMessage message) {
        // 幂等检查必须在扣库存【之前】—— 否则重复消息会重复扣一次 DB 库存
        Order exist = orderMapper.getByRequestId(message.getRequestId());
        if (exist != null) {
            log.info("重复消息，返回已有订单 requestId={} orderId={}",
                    message.getRequestId(), exist.getOrderId());
            return CreateOrderResult.ok(String.valueOf(exist.getOrderId()));
        }
        // 已 giveUp / 已补偿：ACK 丢失导致的重投不能再创单，否则会和库存归还打架
        Outbox outbox = outboxService.lambdaQuery().eq(Outbox::getRequestId, message.getRequestId()).one();
        if (outbox != null
                && (Objects.equals(outbox.getStatus(), OutboxStatus.FAILED.getCode())
                || Objects.equals(outbox.getStatus(), OutboxStatus.COMPENSATED.getCode()))) {
            throw new BusinessException(ErrorCode.ORDER_REJECTED);
        }

        Product product = productService.getById(message.getProductId());
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_EXISTS);
        }
        if (!inventoryService.decreaseStock(message.getProductId(), message.getQuantity())) {
            throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT);
        }
        Order order = Order.builder()
                .productId(message.getProductId())
                .quantity(message.getQuantity())
                .userId(message.getUserId())
                .amount(product.getPrice().multiply(BigDecimal.valueOf(message.getQuantity())))
                .payDeadline(LocalDateTime.now().plusMinutes(30))
                .requestId(message.getRequestId())
                .status(OrderStatus.WAIT_PAY.getCode())
                .build();
        // 并发下两个消费者可能同时走到这里，第二个会撞 uk_order_request 抛 DuplicateKeyException
        // → 整个事务回滚（含刚扣的库存）→ MQ 重试 → 下次走上面的幂等分支返回。结果仍然正确
        save(order);

        //订单建成 消息标记为已消费
        outboxService.lambdaUpdate()
                .eq(Outbox::getRequestId, message.getRequestId())
                .in(Outbox::getStatus, OutboxStatus.PENDING.getCode(), OutboxStatus.SENT.getCode())
                .set(Outbox::getStatus, OutboxStatus.CONSUMED.getCode())
                .update();
        // 延迟关单必须在事务提交之后发：发成功而回滚会多出幽灵关单消息；提交后发送失败由定时任务兜底
        Long orderId = order.getOrderId();
        scheduleDelayCloseAfterCommit(orderId);
        return CreateOrderResult.ok(String.valueOf(orderId));
    }

    private void scheduleDelayCloseAfterCommit(Long orderId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendDelayCloseMessage(orderId);
                }
            });
            return;
        }
        sendDelayCloseMessage(orderId);
    }

    private void sendDelayCloseMessage(Long orderId) {
        try {
            rocketMQTemplate.syncSend(
                    DelayCloseOrderMessage.TOPIC,
                    MessageBuilder.withPayload(new DelayCloseOrderMessage(orderId)).build(),
                    3000L,
                    DelayCloseOrderMessage.DELAY_LEVEL_PAY_WINDOW);
        } catch (Exception e) {
            log.warn("延迟关单消息发送失败，依赖定时任务兜底 orderId={}", orderId, e);
        }
    }

    @Override
    public Order getOrder(Long orderId) {
        Order order = getById(orderId);
        if (Objects.isNull(order)) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    @Override
    public Order getOrderForUpdate(Long orderId) {
        Order order = orderMapper.selectForUpdate(orderId);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    @Override
    public boolean updateStatus(Long orderId, int fromStatus, int toStatus) {
        return baseMapper.updateStatus(orderId, fromStatus, toStatus) == 1;
    }

    @Override
    public boolean extendPayDeadline(Long orderId, LocalDateTime deadline) {
        return baseMapper.extendPayDeadline(orderId, deadline) == 1;
    }

    @Override
    public List<Order> listExpiredWaitPay(LocalDateTime deadline, int limit) {
        return baseMapper.listExpiredWaitPay(deadline, limit);
    }

    /**
     * 时间片 + 重试队列关单（定时兜底）。
     * <p>每轮工作量有上限：重试最多一批；每个时间片最多一批，不掏空。
     * 30s 再来一轮继续消化，避免「必须本轮关完」拖垮调度。
     */
    @Override
    public int closeExpiredOrders(int batchSize) {
        int limit = batchSize > 0 ? batchSize : closeBatchSize;
        LocalDateTime now = LocalDateTime.now().withNano(0);
        int closed = 0;

        // ① 失败退避：每轮最多一批
        closed += drainCloseRetries(now, limit);

        // ② 只扫最近 maxSlices 片，片与片可并行；每片只捞一批
        int sliceMin = Math.max(1, sliceMinutes);
        int maxSlices = Math.max(1, maxSlicesPerTick);
        LocalDateTime from = now.minusMinutes((long) sliceMin * maxSlices);
        List<CompletableFuture<Integer>> futures = new ArrayList<>();
        while (from.isBefore(now)) {
            LocalDateTime to = from.plusMinutes(sliceMin);
            if (to.isAfter(now)) {
                to = now;
            }
            LocalDateTime sliceFrom = from;
            LocalDateTime sliceTo = to;
            futures.add(CompletableFuture.supplyAsync(
                    () -> processSliceOnce(sliceFrom, sliceTo, now, limit), orderCloseExecutor));
            from = to;
        }
        for (CompletableFuture<Integer> future : futures) {
            try {
                closed += future.join();
            } catch (Exception e) {
                log.warn("时间片关单任务失败", e);
            }
        }

        // ③ 滑窗外漏单兜底：延迟 MQ 丢失且从未 claim 的超时单，next_close_at 为空会滑出窗口
        closed += drainStaleExpired(now, limit);
        return closed;
    }

    /** 窗口外仍过期且可关的订单（含 next_close_at 为空的漏单）。 */
    private int drainStaleExpired(LocalDateTime now, int limit) {
        int closed = 0;
        List<Order> stale = orderMapper.listCloseableExpiredOrders(now, limit);
        for (Order order : stale) {
            if (tryCloseIfExpired(order.getOrderId())) {
                closed++;
            }
        }
        return closed;
    }

    /** 扫 next_close_at 到期的失败单，每轮最多 limit 条。 */
    private int drainCloseRetries(LocalDateTime now, int limit) {
        int closed = 0;
        List<Order> retries = orderMapper.listCloseableCloseRetries(now, limit);
        for (Order order : retries) {
            if (tryCloseIfExpired(order.getOrderId())) {
                closed++;
            }
        }
        return closed;
    }

    /**
     * 一个时间片本轮只处理一批（LIMIT），剩的下轮再来。
     */
    private int processSliceOnce(LocalDateTime from, LocalDateTime to, LocalDateTime now, int limit) {
        List<Order> batch = orderMapper.listCloseableInDeadlineSlice(from, to, now, limit);
        if (batch.isEmpty()) {
            return 0;
        }
        int closed = 0;
        for (Order order : batch) {
            if (tryCloseIfExpired(order.getOrderId())) {
                closed++;
            }
        }
        return closed;
    }

    /**
     * 延迟 MQ / 定时任务共用入口。以【当前】payDeadline 为准，避免首付延时后被旧延迟消息误关。
     */
    @Override
    public boolean tryCloseIfExpired(Long orderId) {
        Order order = getById(orderId);
        if (order == null) {
            return false;
        }
        if (!Objects.equals(order.getStatus(), OrderStatus.WAIT_PAY.getCode())) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        if (order.getPayDeadline() == null || order.getPayDeadline().isAfter(now)) {
            return false;
        }
        LocalDateTime lease = now.plusMinutes(2).withNano(0);
        if (orderMapper.claimClose(orderId, now, lease) != 1) {
            return false;
        }
        try {
            boolean closed = closeOne(order);
            orderMapper.finishClose(orderId, lease, now.plusSeconds(15), null);
            return closed;
        } catch (Exception e) {
            String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            long delay = Math.min(600L, 15L * (1L << Math.min(
                    order.getCloseRetryCount() == null ? 0 : order.getCloseRetryCount(), 6)));
            orderMapper.finishClose(orderId, lease, LocalDateTime.now().plusSeconds(delay),
                    error.substring(0, Math.min(error.length(), 1000)));
            log.warn("关单失败，等待下轮重试 orderId={}", orderId, e);
            return false;
        }
    }

    /**
     * 关一单。核心是【先关渠道、再关本地】——
     * 只关本地不会让用户手上的二维码失效，他随时能扫了付钱。
     */
    private boolean closeOne(Order order) {
        Payment payment = paymentService.getByOrderId(order.getOrderId());
        if (payment == null) {
            // 从没发起过支付 → 渠道那边没有凭证 → 直接关
            return orderCloseService.closeLocal(order, null);
        }
        if (PaymentStatus.of(payment.getStatus()) == PaymentStatus.SUCCESS) {
            PayQueryStatus queried = paymentReconcileService.queryAndSettle(payment.getPaymentId());
            if (queried == PayQueryStatus.PAID) {
                return false;
            }
            // 本地 SUCCESS 但渠道 CLOSED/REFUND/未付：不可自动关单，避免误关；记错误由退避重试并依赖人工
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "本地支付成功与渠道查单结果冲突 status=" + queried + "，需要核对");
        }

        // 0=已创建 1=待支付：渠道那边可能还有一张能用的凭证，必须先关掉
        ClosePayResult result;
        try {
            PaymentChannel channel = channelFactory.resolve(payment.getChannel());
            result = channel.closePay(ClosePayRequest.of(
                    payment.getPaymentId(), payment.getOrderId(), payment.getChannel()));
        } catch (Exception e) {
            // 关渠道失败 → 状态未知 → 【绝不能关本地】，下轮重试
            log.warn("关闭渠道支付凭证失败，本轮不关单 orderId={} paymentId={}",
                    order.getOrderId(), payment.getPaymentId(), e);
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "渠道关单结果未知，本地保持待支付: " + e.getMessage());
        }
        if (result == null || result.getStatus() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "渠道未明确确认支付凭证已关闭");
        }
        if (result.getStatus() == ClosePayStatus.ALREADY_PAID) {
            // 用户在关单前付掉了。本地没有通知号（回调可能压根没来），
            // 主动查一次渠道把账入进去，否则这单会永远卡在待支付
            log.warn("渠道显示已支付，改为主动查单入账 orderId={} paymentId={}",
                    order.getOrderId(), payment.getPaymentId());
            if (!paymentReconcileService.settleIfPaid(payment.getPaymentId())) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "渠道关单称已支付，但查单未确认支付，本轮保持待支付");
            }
            return false;
        }
        if (result.getStatus() != ClosePayStatus.CLOSED && result.getStatus() != ClosePayStatus.NOT_FOUND) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "渠道关单结果不能用于本地关闭");
        }
        // NOT_FOUND 再确认一次，不能把错误码的歧义当成没付。
        if (result.getStatus() == ClosePayStatus.NOT_FOUND) {
            PayQueryStatus queried = paymentReconcileService.queryAndSettle(payment.getPaymentId());
            if (queried == PayQueryStatus.PAID) return false;
            if (queried != PayQueryStatus.NOT_FOUND && queried != PayQueryStatus.CLOSED) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "渠道关单称不存在但查单仍未关闭，本轮不关本地");
            }
        }
        // CLOSED / 确认未付款的 NOT_FOUND → 本地事务还会重新锁单、重读支付状态。
        return orderCloseService.closeLocal(order, payment);
    }

}
