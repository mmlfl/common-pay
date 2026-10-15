package com.commonpay.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.commonpay.trade.entity.Inventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {
    @Select("SELECT * FROM trade_inventory WHERE product_id=#{productId} FOR UPDATE")
    com.commonpay.trade.entity.Inventory lockByProductId(@Param("productId") Long productId);

    /**
     * 下单占库存。影响行数=1 成功；=0 库存不足。
     */
    int decreaseStock(@Param("productId") Long productId, @Param("quantity") int quantity);

    /**
     * 超时关单还库存。
     */
    int increaseStock(@Param("productId") Long productId, @Param("quantity") int quantity);
}
