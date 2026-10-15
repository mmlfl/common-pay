package com.commonpay.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.commonpay.trade.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {
    @Update("""
        UPDATE trade_order SET next_close_at=#{lease},close_retry_count=close_retry_count+1
        WHERE order_id=#{id} AND status=0 AND pay_deadline<=#{now}
          AND (next_close_at IS NULL OR next_close_at<=#{now})
        """)
    int claimClose(@Param("id") Long id,@Param("now") LocalDateTime now,@Param("lease") LocalDateTime lease);

    @Update("""
        UPDATE trade_order SET next_close_at=#{next},last_close_error=#{error}
        WHERE order_id=#{id} AND status=0 AND next_close_at=#{lease}
        """)
    int finishClose(@Param("id") Long id,@Param("lease") LocalDateTime lease,
                    @Param("next") LocalDateTime next,@Param("error") String error);

    /**
     * 条件更新状态，用于状态机防并发（如仅待支付可改已支付）。
     * @return 影响行数
     */
    int updateStatus(@Param("orderId") Long orderId,
                     @Param("fromStatus") int fromStatus,
                     @Param("toStatus") int toStatus);

    /**
     * 关单 CAS。<b>必须带 pay_deadline 条件</b> ——
     * 首次创建支付单时 deadline 会被推后（给用户留足支付时间），
     * 定时任务可能拿着"推后之前"的查询结果来关，那样就会关掉刚被延长的订单。
     */
    int closeIfExpired(@Param("orderId") Long orderId, @Param("now") LocalDateTime now);

    /**
     * 推后支付截止时间。只用于首次创建支付单时给用户留足时间，调用方须先持有订单行锁。
     */
    int extendPayDeadline(@Param("orderId") Long orderId, @Param("deadline") LocalDateTime deadline);

    Order selectForUpdate(@Param("orderId") Long orderId);

    List<Order> listExpiredWaitPay(@Param("deadline") LocalDateTime deadline, @Param("limit") int limit);

    /**
     * @deprecated 改用时间片 {@link #listCloseableInDeadlineSlice} + {@link #listCloseableCloseRetries}
     */
    List<Order> listCloseableExpiredOrders(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /**
     * 时间片候选：只扫 pay_deadline ∈ [from, to)，避免 status=待支付 AND pay_deadline&lt;now 一次命中过大集合。
     */
    List<Order> listCloseableInDeadlineSlice(@Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to,
                                            @Param("now") LocalDateTime now,
                                            @Param("limit") int limit);

    /**
     * 关单失败退避到期：按 next_close_at 取，与时间片互补。
     */
    List<Order> listCloseableCloseRetries(@Param("now") LocalDateTime now, @Param("limit") int limit);

    Order getByRequestId(@Param("requestId")String requestId);
}
