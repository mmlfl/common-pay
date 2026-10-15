package com.commonpay.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 库存表 trade_inventory。
 * 自有主键 id；一个商品对应一条库存（product_id 唯一）。
 * 第一期：下单扣减 cnt，超时关单加回，支付成功不再扣。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("trade_inventory")
public class Inventory extends BaseEntity {

    /** 库存主键，数据库自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关联商品 ID（对应 trade_product.product_id，唯一） */
    private Long productId;

    /** 当前可售数量；下单预扣会减少，超时取消会加回 */
    private Integer cnt;
}
