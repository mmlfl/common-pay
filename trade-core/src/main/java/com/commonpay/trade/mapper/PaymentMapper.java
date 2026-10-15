package com.commonpay.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.commonpay.trade.entity.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {

    @Select("""
        SELECT p.* FROM trade_payment p JOIN trade_order o ON o.order_id=p.order_id
        WHERE p.status IN (0,1,3) AND p.created_at <= #{olderThan}
          AND (p.next_reconcile_at IS NULL OR p.next_reconcile_at <= #{now})
          AND (o.status IN (0,1) OR (o.status=2 AND o.updated_at >= #{since}))
        ORDER BY COALESCE(p.next_reconcile_at,p.created_at), p.payment_id LIMIT #{limit}
        """)
    List<Payment> listReconcileDue(@Param("now") LocalDateTime now, @Param("olderThan") LocalDateTime olderThan,
                                   @Param("since") LocalDateTime since, @Param("limit") int limit);

    @Update("""
        UPDATE trade_payment SET next_reconcile_at=#{until}
        WHERE payment_id=#{id} AND status IN (0,1,3)
          AND (next_reconcile_at IS NULL OR next_reconcile_at <= #{now})
        """)
    int claimReconcile(@Param("id") Long id, @Param("now") LocalDateTime now, @Param("until") LocalDateTime until);

    @Update("""
        UPDATE trade_payment SET next_reconcile_at=#{next},last_reconcile_error=#{error}
        WHERE payment_id=#{id} AND next_reconcile_at=#{claimUntil} AND status IN (0,1,3)
        """)
    int finishReconcile(@Param("id") Long id, @Param("claimUntil") LocalDateTime claimUntil,
                        @Param("next") LocalDateTime next, @Param("error") String error);

    int updateStatus(@Param("paymentId") Long paymentId,
                     @Param("fromStatus") int fromStatus,
                     @Param("toStatus") int toStatus);

    Payment selectForUpdate(@Param("paymentId") Long paymentId);

    int markSuccess(@Param("paymentId") Long paymentId,
                    @Param("channelTradeNo") String channelTradeNo,
                    @Param("notifyId") String notifyId);
}
