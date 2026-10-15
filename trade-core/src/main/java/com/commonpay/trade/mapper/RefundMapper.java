package com.commonpay.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.commonpay.trade.entity.Refund;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RefundMapper extends BaseMapper<Refund> {
    @Select("SELECT * FROM trade_refund WHERE refund_id=#{id} FOR UPDATE")
    Refund lock(@Param("id") Long id);

    @Select("""
        SELECT * FROM trade_refund
        WHERE status IN (0,1) AND next_retry_at <= #{now}
          AND (claim_until IS NULL OR claim_until <= #{now})
        ORDER BY next_retry_at, refund_id LIMIT #{limit}
        """)
    List<Refund> due(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("""
        UPDATE trade_refund SET claim_token=#{token}, claim_until=#{until}
        WHERE refund_id=#{id} AND status IN (0,1) AND next_retry_at <= #{now}
          AND (claim_until IS NULL OR claim_until <= #{now})
        """)
    int claim(@Param("id") Long id, @Param("token") String token,
              @Param("now") LocalDateTime now, @Param("until") LocalDateTime until);

    @Update("""
        UPDATE trade_refund SET retry_count=retry_count+1, last_error=#{error},
          next_retry_at=#{next}, claim_token=NULL, claim_until=NULL
        WHERE refund_id=#{id} AND claim_token=#{token} AND status IN (0,1)
        """)
    int retry(@Param("id") Long id, @Param("token") String token,
              @Param("next") LocalDateTime next, @Param("error") String error);

    @Update("""
        UPDATE trade_refund SET status=3,last_error=#{error},claim_token=NULL,claim_until=NULL
        WHERE refund_id=#{id} AND claim_token=#{token} AND status IN (0,1)
        """)
    int manual(@Param("id") Long id,@Param("token") String token,@Param("error") String error);
}
