package com.commonpay.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 商品表 trade_product。
 * 可售卖单元（demo 为虚拟票）；主键数据库自增。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("trade_product")
public class Product extends BaseEntity {

    /** 商品主键，数据库自增 */
    @TableId(value = "product_id", type = IdType.AUTO)
    private Long productId;

    /** 商品标题，如「Demo虚拟票-普通票」 */
    private String title;

    /** 单价，单位：元，保留两位小数 */
    private BigDecimal price;

    /**
     * 上下架状态。
     * 0=下架，1=上架（见 {@link com.commonpay.trade.enums.ProductStatus}）
     */
    private Integer status;
}
