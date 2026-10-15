package com.commonpay.trade.service.impl;

import com.commonpay.pay.api.dto.RefundResult;
import com.commonpay.pay.api.enums.RefundQueryStatus;
import com.commonpay.trade.entity.Refund;
import com.commonpay.trade.enums.RefundStatus;
import com.commonpay.trade.common.ErrorCode;
import com.commonpay.trade.exception.BusinessException;
import com.commonpay.trade.mapper.RefundMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Objects;

/** 只负责短事务落库；回调与退款查询共用同一套校验和状态转换。 */
@Service
@RequiredArgsConstructor
public class RefundStateService {
    private final RefundMapper mapper;

    @Transactional(rollbackFor = Exception.class)
    public void apply(Long refundId, String channel, String claimToken, RefundResult result) {
        Refund refund = mapper.lock(refundId);
        if (refund == null || !refund.getChannel().equalsIgnoreCase(channel)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "退款单不存在或渠道不匹配");
        }
        if (claimToken != null && !claimToken.equals(refund.getClaimToken())) {
            // 回调可能已先落库并清空 claim；本地已 SUCCESS 则视为幂等，否则抛错让任务重试，禁止静默丢结果
            if (refund.getStatus() != null && refund.getStatus() == RefundStatus.SUCCESS.getCode()) {
                return;
            }
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "退款认领令牌不匹配，稍后重试");
        }
        if (result == null || result.getStatus() == null
                || result.getStatus() == RefundQueryStatus.NOT_FOUND
                || !refundId.toString().equals(result.getRefundNo())
                || !refund.getPaymentId().equals(result.getPaymentId())
                || !refund.getChannelTradeNo().equals(result.getChannelTradeNo())
                || result.getAmount() == null || refund.getAmount().compareTo(result.getAmount()) != 0
                || result.getChannelRefundNo() == null || result.getChannelRefundNo().isBlank()) {
            throw new BusinessException(ErrorCode.REFUND_MISMATCH, "退款结果与本地退款单不匹配");
        }
        if (refund.getChannelRefundNo() != null
                && !Objects.equals(refund.getChannelRefundNo(), result.getChannelRefundNo())) {
            throw new BusinessException(ErrorCode.REFUND_MISMATCH, "同一退款单返回不同的渠道退款流水");
        }
        if (refund.getStatus() == RefundStatus.SUCCESS.getCode()) return;
        // 已明确异常的退款不被迟到的 PROCESSING 覆盖；最终成功可以解除人工状态。
        if (refund.getStatus() == RefundStatus.MANUAL.getCode()
                && result.getStatus() == RefundQueryStatus.PROCESSING) return;
        refund.setChannelRefundNo(result.getChannelRefundNo());
        refund.setClaimToken(null);
        refund.setClaimUntil(null);
        refund.setNextRetryAt(LocalDateTime.now().plusMinutes(1));
        switch (result.getStatus()) {
            case SUCCESS -> {
                refund.setStatus(RefundStatus.SUCCESS.getCode());
                refund.setRefundedAt(result.getRefundedAt() == null ? LocalDateTime.now() : result.getRefundedAt());
                refund.setLastError(null);
            }
            case PROCESSING -> {
                refund.setStatus(RefundStatus.PROCESSING.getCode());
                refund.setLastError(null);
            }
            case CLOSED, ABNORMAL -> {
                refund.setStatus(RefundStatus.MANUAL.getCode());
                refund.setLastError("渠道退款状态: " + result.getStatus() + "，需人工核对");
            }
            default -> throw new BusinessException(ErrorCode.BUSINESS_ERROR, "不能据此更新退款状态");
        }
        // updateById 默认忽略 null，认领字段用显式 SQL/Wrapper 清空。
        mapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Refund>()
                .eq(Refund::getRefundId, refundId)
                .set(Refund::getStatus, refund.getStatus())
                .set(Refund::getChannelRefundNo, refund.getChannelRefundNo())
                .set(Refund::getNextRetryAt, refund.getNextRetryAt())
                .set(Refund::getRefundedAt, refund.getRefundedAt())
                .set(Refund::getClaimToken, null).set(Refund::getClaimUntil, null)
                .set(Refund::getLastError, refund.getLastError()));
    }
}
