package com.commonpay.trade.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateOrderRequest {

    @NotBlank(message = "请提供用户标识")
    @Size(max = 64, message = "用户标识不能超过 64 个字符")
    private String userId;

    @NotNull(message = "请提供商品 ID")
    @Positive(message = "商品 ID 必须大于零")
    private Long productId;

    @NotNull(message = "请提供购买数量")
    @Min(value = 1, message = "购买数量至少为 1")
    private Integer quantity;

    @NotBlank(message = "请提供受理号")
    @Size(max = 64, message = "受理号不能超过 64 个字符")
    private String requestId;
}
