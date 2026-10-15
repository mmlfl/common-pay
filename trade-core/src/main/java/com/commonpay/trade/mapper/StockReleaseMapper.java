package com.commonpay.trade.mapper;
import com.commonpay.trade.entity.StockRelease;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;
@Mapper
public interface StockReleaseMapper {
    @Select("SELECT COALESCE(MAX(release_id),0) FROM trade_stock_release WHERE product_id=#{productId}")
    long latestReleaseId(@Param("productId") Long productId);
    @Insert("INSERT INTO trade_stock_release(order_id,product_id,quantity) VALUES(#{orderId},#{productId},#{quantity})")
    int insert(@Param("orderId") Long orderId, @Param("productId") Long productId, @Param("quantity") int quantity);
    @Select("SELECT * FROM trade_stock_release WHERE status=0 AND next_retry_at<=#{now} ORDER BY next_retry_at,order_id LIMIT 100")
    List<StockRelease> due(@Param("now") LocalDateTime now);
    @Update("""
        UPDATE trade_stock_release SET next_retry_at=#{lease}
        WHERE order_id=#{id} AND status=0 AND next_retry_at<=#{now}
        """)
    int claim(@Param("id") Long id,@Param("now") LocalDateTime now,@Param("lease") LocalDateTime lease);
    @Update("""
        UPDATE trade_stock_release SET status=1,last_error=NULL
        WHERE order_id=#{id} AND status=0 AND next_retry_at=#{lease}
        """)
    int done(@Param("id") Long id,@Param("lease") LocalDateTime lease);
    @Update("""
        UPDATE trade_stock_release SET retry_count=retry_count+1,next_retry_at=#{next},last_error=#{error}
        WHERE order_id=#{id} AND status=0 AND next_retry_at=#{lease}
        """)
    int retry(@Param("id") Long id,@Param("lease") LocalDateTime lease,@Param("next") LocalDateTime next,
              @Param("error") String error);
}
