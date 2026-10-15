package com.commonpay.trade.redis;

import com.alibaba.fastjson2.JSON;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 操作入口。业务代码统一走这里，不要直接注入 RedisTemplate。
 * <p>
 * Redis 为 common-pay 强制依赖；由 starter 装配 {@code RedisUtil} Bean，宿主须配置 {@code spring.data.redis.*}。
 */
public class RedisUtil {
    private static final DefaultRedisScript<Long> COMPARE_AND_SET = new DefaultRedisScript<>("""
        if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
        redis.call('SET', KEYS[1], ARGV[2])
        return 1
        """, Long.class);

    private static final DefaultRedisScript<Long> RETURN_RESERVATION = new DefaultRedisScript<>("""
        if redis.call('EXISTS', KEYS[2]) == 1 then return 0 end
        if redis.call('EXISTS', KEYS[1]) == 0 then return -1 end
        redis.call('INCRBY', KEYS[1], ARGV[1])
        redis.call('SET', KEYS[2], '1')
        return 1
        """, Long.class);

    /** 使用与 set/get 相同的参数序列化，防止迟到请求覆盖别人的幂等记录。 */
    public boolean compareAndSet(String key, String expected, String replacement) {
        Long result = redisTemplate.execute(COMPARE_AND_SET,
                Collections.singletonList(fullKey(key)), expected, replacement);
        if (result == null) throw new IllegalStateException("幂等更新脚本无结果");
        return result == 1L;
    }

    /** 已确认未受理时归还本次预扣。响应丢失后重试也不会重复增加库存。 */
    public void returnReservationOnce(Long productId, int quantity, String attemptToken) {
        requirePositiveDelta(quantity);
        Long result = stringRedisTemplate.execute(RETURN_RESERVATION,
                java.util.List.of(fullKey(RedisKeys.stock(productId)),
                        fullKey(RedisKeys.reservationReturned(attemptToken))), String.valueOf(quantity));
        if (result == null || result < 0) throw new IllegalStateException("预扣归还未确认，保留请求等待核对");
    }

    private static final DefaultRedisScript<Long> RELEASE_STOCK = new DefaultRedisScript<>("""
        if redis.call('EXISTS', KEYS[2]) == 1 then return 0 end
        if redis.call('EXISTS', KEYS[1]) == 0 then return -1 end
        local baseline = tonumber(redis.call('GET', KEYS[3]) or '0')
        if tonumber(ARGV[2]) > baseline then redis.call('INCRBY', KEYS[1], ARGV[1]) end
        redis.call('SET', KEYS[2], '1')
        return 1
        """, Long.class);

    private static final DefaultRedisScript<Long> SEED_STOCK = new DefaultRedisScript<>("""
        if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
        redis.call('SET', KEYS[1], ARGV[1])
        redis.call('SET', KEYS[2], ARGV[2])
        return 1
        """, Long.class);

    /** 必须由持有 DB 库存行锁的冷路径调用。两项写入原子执行。 */
    public void seedStockIfAbsent(Long productId, int quantity, long includedReleaseId) {
        Long result = stringRedisTemplate.execute(SEED_STOCK,
                java.util.List.of(fullKey(RedisKeys.stock(productId)),fullKey(RedisKeys.stockReleaseBaseline(productId))),
                String.valueOf(quantity),String.valueOf(includedReleaseId));
        if (result == null) throw new IllegalStateException("Redis 预热脚本无结果");
    }

    /** 回库、检查预热包含范围、去重同一脚本。缺失缓存时保留义务并重试。 */
    public void releaseStockOnce(Long orderId, Long productId, long releaseId, int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("回库数量必须大于零");
        Long result = stringRedisTemplate.execute(RELEASE_STOCK,
                java.util.List.of(fullKey(RedisKeys.stock(productId)), fullKey(RedisKeys.stockReleased(orderId)),
                        fullKey(RedisKeys.stockReleaseBaseline(productId))),
                String.valueOf(quantity), String.valueOf(releaseId));
        if (result == null || result < 0) throw new IllegalStateException("Redis 回库时库存缓存缺失，请重新预热");
    }

    private static final DefaultRedisScript<Long> DECR_IF_ENOUGH = new DefaultRedisScript<>(
            """
                    local current = redis.call('GET', KEYS[1])
                    if current == false then
                      return -2
                    end
                    current = tonumber(current)
                    local delta = tonumber(ARGV[1])
                    if current < delta then
                      return -1
                    end
                    return redis.call('DECRBY', KEYS[1], delta)
                    """,
            Long.class);

    /** 预扣成功同时登记崩溃对账义务，缩小「扣了库存但没写下义务」窗口。 */
    private static final DefaultRedisScript<Long> DECR_IF_ENOUGH_WITH_DEBT = new DefaultRedisScript<>(
            """
                    local current = redis.call('GET', KEYS[1])
                    if current == false then
                      return -2
                    end
                    current = tonumber(current)
                    local delta = tonumber(ARGV[1])
                    if current < delta then
                      return -1
                    end
                    local left = redis.call('DECRBY', KEYS[1], delta)
                    redis.call('SET', KEYS[2], ARGV[2], 'EX', tonumber(ARGV[5]))
                    redis.call('ZADD', KEYS[3], tonumber(ARGV[3]), ARGV[4])
                    return left
                    """,
            Long.class);

    private static final DefaultRedisScript<Long> CLEAR_RESERVATION_DEBT = new DefaultRedisScript<>(
            """
                    redis.call('DEL', KEYS[1])
                    redis.call('ZREM', KEYS[2], ARGV[1])
                    return 1
                    """,
            Long.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final String keyPrefix;

    public RedisUtil(RedisTemplate<String, Object> redisTemplate,
                     StringRedisTemplate stringRedisTemplate,
                     String keyPrefix) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.keyPrefix = keyPrefix == null ? "" : keyPrefix;
    }

    public String fullKey(String key) {
        requireKey(key);
        return keyPrefix.isEmpty() ? key : keyPrefix + key;
    }

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(fullKey(key), value);
    }

    public void set(String key, Object value, Duration ttl) {
        requirePositiveTtl(ttl);
        redisTemplate.opsForValue().set(fullKey(key), value, ttl);
    }

    public boolean setIfAbsent(String key, Object value) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(fullKey(key), value));
    }

    public boolean setIfAbsent(String key, Object value, Duration ttl) {
        requirePositiveTtl(ttl);
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(fullKey(key), value, ttl));
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) redisTemplate.opsForValue().get(fullKey(key));
    }

    public <T> T get(String key, Class<T> type) {
        Objects.requireNonNull(type, "type");
        Object value = redisTemplate.opsForValue().get(fullKey(key));
        return value == null ? null : type.cast(value);
    }

    public boolean delete(String key) {
        return Boolean.TRUE.equals(redisTemplate.delete(fullKey(key)));
    }

    public long delete(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return 0L;
        }
        Long n = redisTemplate.delete(keys.stream().map(this::fullKey).toList());
        return n == null ? 0L : n;
    }

    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(fullKey(key)));
    }

    public boolean expire(String key, Duration ttl) {
        requirePositiveTtl(ttl);
        return Boolean.TRUE.equals(redisTemplate.expire(fullKey(key), ttl));
    }

    public Long getExpireSeconds(String key) {
        return redisTemplate.getExpire(fullKey(key), TimeUnit.SECONDS);
    }

    public void setCounter(String key, long value) {
        stringRedisTemplate.opsForValue().set(fullKey(key), Long.toString(value));
    }

    public void setCounter(String key, long value, Duration ttl) {
        requirePositiveTtl(ttl);
        stringRedisTemplate.opsForValue().set(fullKey(key), Long.toString(value), ttl);
    }

    /** 仅当 key 不存在时写入计数器，用于从 DB 预热且避免覆盖。 */
    public boolean setCounterIfAbsent(String key, long value) {
        return Boolean.TRUE.equals(
                stringRedisTemplate.opsForValue().setIfAbsent(fullKey(key), Long.toString(value)));
    }

    public Long increment(String key, long delta) {
        requirePositiveDelta(delta);
        return stringRedisTemplate.opsForValue().increment(fullKey(key), delta);
    }

    public Long decrement(String key, long delta) {
        requirePositiveDelta(delta);
        return stringRedisTemplate.opsForValue().decrement(fullKey(key), delta);
    }

    public DecrResult decrementIfEnough(String key, long delta) {
        requirePositiveDelta(delta);
        Long result = stringRedisTemplate.execute(
                DECR_IF_ENOUGH,
                Collections.singletonList(fullKey(key)),
                Long.toString(delta));
        return toDecrResult(result);
    }

    /**
     * 预扣库存并原子登记义务。义务 TTL 应明显长于对账门槛，避免扫不到详情。
     */
    public DecrResult decrementIfEnoughWithDebt(Long productId, int quantity, String requestId,
                                                 String attemptToken, Duration debtTtl) {
        requirePositiveDelta(quantity);
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        if (attemptToken == null || attemptToken.isBlank()) {
            throw new IllegalArgumentException("attemptToken must not be blank");
        }
        requirePositiveTtl(debtTtl);
        long createdAt = System.currentTimeMillis();
        ReservationDebt debt = new ReservationDebt();
        debt.setRequestId(requestId);
        debt.setProductId(productId);
        debt.setQuantity(quantity);
        debt.setAttemptToken(attemptToken);
        debt.setCreatedAt(createdAt);
        Long result = stringRedisTemplate.execute(
                DECR_IF_ENOUGH_WITH_DEBT,
                List.of(
                        fullKey(RedisKeys.stock(productId)),
                        fullKey(RedisKeys.reservationDebt(requestId)),
                        fullKey(RedisKeys.reservationDebtIndex())),
                String.valueOf(quantity),
                JSON.toJSONString(debt),
                String.valueOf(createdAt),
                requestId,
                String.valueOf(debtTtl.getSeconds()));
        return toDecrResult(result);
    }

    /** 受理成功或即时回补成功后销账。 */
    public void clearReservationDebt(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }
        stringRedisTemplate.execute(
                CLEAR_RESERVATION_DEBT,
                List.of(
                        fullKey(RedisKeys.reservationDebt(requestId)),
                        fullKey(RedisKeys.reservationDebtIndex())),
                requestId);
    }

    public ReservationDebt getReservationDebt(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return null;
        }
        String raw = stringRedisTemplate.opsForValue().get(fullKey(RedisKeys.reservationDebt(requestId)));
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return JSON.parseObject(raw, ReservationDebt.class);
    }

    /** 拉取创建时间早于 maxScore 的义务 requestId（按时间升序，最多 limit 条）。 */
    public List<String> listDueReservationDebtRequestIds(long maxScoreExclusive, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        Set<String> members = stringRedisTemplate.opsForZSet().rangeByScore(
                fullKey(RedisKeys.reservationDebtIndex()),
                0,
                Math.max(0, maxScoreExclusive - 1),
                0,
                limit);
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(members);
    }

    private static DecrResult toDecrResult(Long result) {
        if (result == null) {
            return DecrResult.fail(RedisErrorCode.UNKNOWN, 0L);
        }
        if (result == -2L) {
            return DecrResult.fail(RedisErrorCode.KEY_MISSING, 0L);
        }
        if (result == -1L) {
            return DecrResult.fail(RedisErrorCode.INSUFFICIENT, 0L);
        }
        if (result < 0L) {
            return DecrResult.fail(RedisErrorCode.UNKNOWN, 0L);
        }
        return DecrResult.ok(result);
    }

    public Long getCounter(String key) {
        String v = stringRedisTemplate.opsForValue().get(fullKey(key));
        return v == null || v.isEmpty() ? null : Long.parseLong(v);
    }

    public boolean expireCounter(String key, Duration ttl) {
        requirePositiveTtl(ttl);
        return Boolean.TRUE.equals(stringRedisTemplate.expire(fullKey(key), ttl));
    }

    public boolean deleteCounter(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.delete(fullKey(key)));
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Redis key must not be null or blank");
        }
    }

    private static void requirePositiveTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("TTL must be positive");
        }
    }

    private static void requirePositiveDelta(long delta) {
        if (delta <= 0) {
            throw new IllegalArgumentException("delta must be positive");
        }
    }
}
