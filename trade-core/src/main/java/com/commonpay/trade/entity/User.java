package com.commonpay.trade.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户表 trade_user。
 * {@code userId} 与订单里的外部用户 ID 同空间，下单链路不强依赖本表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("trade_user")
public class User extends BaseEntity {

    /** 用户主键，与 trade_order.user_id 一致 */
    @TableId(value = "user_id", type = IdType.INPUT)
    private String userId;

    /** 昵称 */
    private String nickname;

    /**
     * 状态。
     * 0=停用，1=正常（见 {@link com.commonpay.trade.enums.UserStatus}）
     */
    private Integer status;
}
