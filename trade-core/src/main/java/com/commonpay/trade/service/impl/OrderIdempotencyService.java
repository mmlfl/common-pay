package com.commonpay.trade.service.impl;

import com.alibaba.fastjson2.JSON;
import com.commonpay.trade.common.ErrorCode;
import com.commonpay.trade.dto.CreateOrderRequest;
import com.commonpay.trade.exception.BaseException;
import com.commonpay.trade.exception.BusinessException;
import com.commonpay.trade.redis.RedisKeys;
import com.commonpay.trade.redis.RedisUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/** 保存/重放业务决定。未知结果不自动过期，避免再次预扣；需要查受理记录或人工核对。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderIdempotencyService {
    private static final Duration TERMINAL_TTL = Duration.ofMinutes(10);
    private final RedisUtil redis;

    public Claim claim(CreateOrderRequest request) {
        String key = RedisKeys.orderIdempotent(request.getRequestId());
        Snapshot proposed = new Snapshot();
        proposed.setState("PROCESSING");
        proposed.setToken(UUID.randomUUID().toString());
        proposed.setUserId(request.getUserId());
        proposed.setProductId(request.getProductId());
        proposed.setQuantity(request.getQuantity());
        String encoded = JSON.toJSONString(proposed);
        // 处理/未知状态不设 TTL，进程退出也不会允许同一请求再次预扣。
        if (redis.setIfAbsent(key, encoded)) return new Claim(key, encoded, proposed, true);
        String previous = redis.get(key, String.class);
        if (previous == null) return new Claim(key, null, proposed, false);
        Snapshot stored = decode(previous);
        if (stored.getToken() != null && (!Objects.equals(stored.getUserId(), request.getUserId())
                || !Objects.equals(stored.getProductId(), request.getProductId())
                || !Objects.equals(stored.getQuantity(), request.getQuantity()))) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        return new Claim(key, previous, stored, false);
    }

    public void replayFailure(Claim claim) {
        Snapshot snapshot = claim.snapshot();
        if ("REJECTED".equals(snapshot.getState())) {
            if (snapshot.isBusiness()) {
                throw toBusinessException(snapshot.getErrorCode(), snapshot.getMessage());
            }
            throw toBaseException(snapshot.getErrorCode(), snapshot.getMessage());
        }
        if ("UNKNOWN".equals(snapshot.getState())) throw new BaseException(ErrorCode.ORDER_RESULT_UNKNOWN);
    }

    public boolean isRejected(Claim claim) { return "REJECTED".equals(claim.snapshot().getState()); }
    public boolean isAccepted(Claim claim) {
        return "ACCEPTED".equals(claim.snapshot().getState()) && claim.snapshot().getToken() != null;
    }

    public boolean isFailed(String requestId) {
        String stored = redis.get(RedisKeys.orderIdempotent(requestId), String.class);
        return stored != null && "REJECTED".equals(decode(stored).getState());
    }

    public void accepted(Claim claim) { complete(claim, "ACCEPTED", null); }
    public void rejected(Claim claim, BusinessException error) { complete(claim, "REJECTED", error); }
    public void unknown(Claim claim) { complete(claim, "UNKNOWN", null); }

    /**
     * 消费端 giveUp 后把幂等位改成 REJECTED，避免 HTTP 重放仍看到 ACCEPTED。
     * 缓存已过期时静默跳过（Outbox FAILED 仍能支撑进度查询）。
     */
    public void markGiveUp(String requestId, String reason) {
        String key = RedisKeys.orderIdempotent(requestId);
        String previous = redis.get(key, String.class);
        if (previous == null) return;
        Snapshot stored = decode(previous);
        if ("REJECTED".equals(stored.getState())) return;
        Snapshot target = JSON.parseObject(JSON.toJSONString(stored), Snapshot.class);
        target.setState("REJECTED");
        target.setBusiness(true);
        target.setErrorCode(ErrorCode.ORDER_REJECTED.name());
        target.setMessage(reason != null && !reason.isBlank() ? reason : ErrorCode.ORDER_REJECTED.getMessage());
        try {
            if (redis.compareAndSet(key, previous, JSON.toJSONString(target))) {
                redis.expire(key, TERMINAL_TTL);
            }
        } catch (RuntimeException e) {
            log.error("giveUp 写回幂等失败 key={}，保留原记录等待核对", key, e);
        }
    }

    private void complete(Claim claim, String state, BusinessException error) {
        if (claim.expected() == null) return;
        Snapshot target = JSON.parseObject(JSON.toJSONString(claim.snapshot()), Snapshot.class);
        target.setState(state);
        target.setBusiness(error != null);
        target.setErrorCode(error == null ? null
                : (error.getErrorCode() != null ? error.getErrorCode().name() : String.valueOf(error.getCode())));
        target.setMessage(error == null ? null : error.getMessage());
        try {
            if (redis.compareAndSet(claim.key(), claim.expected(), JSON.toJSONString(target))
                    && !"UNKNOWN".equals(state)) redis.expire(claim.key(), TERMINAL_TTL);
        } catch (RuntimeException e) {
            // 写回失败不能否认已经落库的受理事实，也不能覆盖原来的业务错误。
            log.error("保存幂等结果失败 key={} state={}，保留原记录等待核对", claim.key(), state, e);
        }
    }

    private Snapshot decode(String raw) {
        if (raw.startsWith("{")) {
            try {
                Snapshot snapshot = JSON.parseObject(raw, Snapshot.class);
                if (snapshot == null || !java.util.Set.of("PROCESSING", "ACCEPTED", "REJECTED", "UNKNOWN")
                        .contains(snapshot.getState())) throw new IllegalArgumentException("无效的幂等状态");
                if ("REJECTED".equals(snapshot.getState())) ErrorCode.valueOf(snapshot.getErrorCode());
                return snapshot;
            } catch (RuntimeException e) {
                throw new BaseException(ErrorCode.ORDER_RESULT_UNKNOWN, e);
            }
        }
        // 升级前的字符串记录继续可读；失败记录改为重放异常。
        Snapshot legacy = new Snapshot();
        legacy.setState("PROCESSING");
        ErrorCode error = switch (raw) {
            case "STOCK_INSUFFICIENT" -> ErrorCode.STOCK_INSUFFICIENT;
            case "PRODUCT_NOT_EXISTS" -> ErrorCode.PRODUCT_NOT_EXISTS;
            case "REDIS_KEY_MISSING" -> ErrorCode.STOCK_NOT_READY;
            case "MQ_SEND_FAILED" -> ErrorCode.ORDER_ACCEPT_FAILED;
            default -> null;
        };
        if (error != null) {
            legacy.setState("REJECTED");
            legacy.setBusiness(true);
            legacy.setErrorCode(error.name());
            legacy.setMessage(error.getMessage());
        } else if ("ACCEPTED".equals(raw) || "OK".equals(raw)) {
            legacy.setState("ACCEPTED");
        } else if (!"PROCESSING".equals(raw)) {
            legacy.setState("UNKNOWN");
        }
        return legacy;
    }

    private static BusinessException toBusinessException(String storedCode, String message) {
        ErrorCode enumCode = tryParseErrorCode(storedCode);
        if (enumCode != null) {
            return new BusinessException(enumCode, message);
        }
        try {
            return new BusinessException(Integer.parseInt(storedCode), message != null ? message : "业务操作未完成");
        } catch (NumberFormatException e) {
            return new BusinessException(ErrorCode.BUSINESS_ERROR, message);
        }
    }

    private static BaseException toBaseException(String storedCode, String message) {
        ErrorCode enumCode = tryParseErrorCode(storedCode);
        if (enumCode != null) {
            return new BaseException(enumCode, message);
        }
        try {
            return new BaseException(Integer.parseInt(storedCode), message != null ? message : ErrorCode.SYSTEM_ERROR.getMessage());
        } catch (NumberFormatException e) {
            return new BaseException(ErrorCode.ORDER_RESULT_UNKNOWN, message);
        }
    }

    private static ErrorCode tryParseErrorCode(String storedCode) {
        if (storedCode == null || storedCode.isBlank()) {
            return null;
        }
        try {
            return ErrorCode.valueOf(storedCode);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public record Claim(String key, String expected, Snapshot snapshot, boolean owner) {}

    @Data
    public static class Snapshot {
        private String state;
        private String token;
        private String userId;
        private Long productId;
        private Integer quantity;
        private String errorCode;
        private String message;
        private boolean business;
    }
}
