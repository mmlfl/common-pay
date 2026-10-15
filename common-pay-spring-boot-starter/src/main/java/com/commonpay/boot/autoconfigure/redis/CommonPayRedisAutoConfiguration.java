package com.commonpay.boot.autoconfigure.redis;

import com.commonpay.trade.redis.RedisUtil;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;

/**
 * Redis 为 common-pay 强制基础设施。提供 JSON {@code commonPayRedisTemplate} 与 {@link RedisUtil}。
 * 宿主必须配置 {@code spring.data.redis.*}。
 */
@AutoConfiguration(after = RedisAutoConfiguration.class)
@EnableConfigurationProperties(CommonPayRedisProperties.class)
public class CommonPayRedisAutoConfiguration {

    public static final String REDIS_TEMPLATE_BEAN = "commonPayRedisTemplate";

    @Bean(name = REDIS_TEMPLATE_BEAN)
    @ConditionalOnMissingBean(name = REDIS_TEMPLATE_BEAN)
    public RedisTemplate<String, Object> commonPayRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);

        GenericJackson2JsonRedisSerializer valueSerializer =
                new GenericJackson2JsonRedisSerializer(buildObjectMapper());
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    @ConditionalOnMissingBean(RedisUtil.class)
    public RedisUtil redisUtil(
            @Qualifier(REDIS_TEMPLATE_BEAN) RedisTemplate<String, Object> redisTemplate,
            StringRedisTemplate stringRedisTemplate,
            CommonPayRedisProperties properties) {
        return new RedisUtil(redisTemplate, stringRedisTemplate, properties.getKeyPrefix());
    }

    private static ObjectMapper buildObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        return mapper;
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient(RedisProperties redis) {
        // 这一版按 ticket-demo 现在的单机 Redis 配置写。
        // 将来切 Sentinel / Cluster / URL 时，先补对应配置，不能悄悄连错库。
        if (StringUtils.hasText(redis.getUrl())
                || redis.getSentinel() != null
                || redis.getCluster() != null
                || StringUtils.hasText(redis.getSsl().getBundle())) {
            throw new IllegalStateException("请为 Redisson 补齐 URL/Sentinel/Cluster/SSL Bundle 配置");
        }

        Config config = new Config();
        config.setCodec(StringCodec.INSTANCE); // 所有实例用同一种商品 ID 编码
        String scheme = redis.getSsl().isEnabled() ? "rediss://" : "redis://";
        SingleServerConfig server = config.useSingleServer()
                .setAddress(scheme + redis.getHost() + ":" + redis.getPort())
                .setDatabase(redis.getDatabase());

        if (StringUtils.hasText(redis.getUsername())) {
            server.setUsername(redis.getUsername());
        }
        if (StringUtils.hasText(redis.getPassword())) {
            server.setPassword(redis.getPassword());
        }
        if (redis.getTimeout() != null) {
            server.setTimeout(Math.toIntExact(redis.getTimeout().toMillis()));
        }
        if (redis.getConnectTimeout() != null) {
            server.setConnectTimeout(Math.toIntExact(redis.getConnectTimeout().toMillis()));
        }
        return Redisson.create(config);
    }
}
