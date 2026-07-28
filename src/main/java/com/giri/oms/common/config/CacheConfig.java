package com.giri.oms.common.config;

import com.giri.oms.product.dto.ProductResponse;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Trimmed from oms-main's CacheConfig: PRODUCTS_CACHE only — INVENTORY_CACHE
 * belonged to a module that doesn't exist in this codebase. Same reasoning as
 * the original (see there for the full Javadoc on why each cache gets its own
 * typed serializer instead of one shared polymorphic one) — not repeated here.
 * No Redisson: this is plain Spring Cache + Lettuce, since product-service has
 * no distributed-locking or rate-limiting need for Redis, only caching.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String PRODUCTS_CACHE = "products";

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .disableCachingNullValues();

        RedisCacheConfiguration productsConfig = base
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(ProductResponse.class)))
                .entryTtl(Duration.ofMinutes(15));

        return builder -> builder.withCacheConfiguration(PRODUCTS_CACHE, productsConfig);
    }
}
