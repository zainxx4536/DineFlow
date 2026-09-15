package com.dineflow.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;

/**
 * 自定义 RedisTemplate 和 SpringCache 的序列化方式
 */
@Configuration
@EnableCaching
public class RedisConfiguration {

    /**
     * Redis JSON 序列化器
     */
    @Bean
    public GenericJackson2JsonRedisSerializer jsonRedisSerializer(
            ObjectMapper objectMapper) {

        // 复制 Spring 已配置好的 ObjectMapper
        ObjectMapper redisObjectMapper = objectMapper.copy();

        // GenericJackson2JsonRedisSerializer 反序列化 Object 时
        // 需要保留实际 Java 类型信息
        redisObjectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        return new GenericJackson2JsonRedisSerializer(
                redisObjectMapper
        );
    }

    /**
     * 自定义 RedisTemplate 序列化方式
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory,
            GenericJackson2JsonRedisSerializer jsonRedisSerializer) {

        RedisTemplate<String, Object> redisTemplate =
                new RedisTemplate<>();

        redisTemplate.setConnectionFactory(connectionFactory);

        // Key 使用 String
        redisTemplate.setKeySerializer(
                RedisSerializer.string()
        );
        redisTemplate.setHashKeySerializer(
                RedisSerializer.string()
        );

        // Value 使用 JSON
        redisTemplate.setValueSerializer(
                jsonRedisSerializer
        );
        redisTemplate.setHashValueSerializer(
                jsonRedisSerializer
        );

        return redisTemplate;
    }

    /**
     * Spring Cache 配置
     */
    @Bean
    public RedisCacheManager redisCacheManager(
            RedisConnectionFactory connectionFactory,
            GenericJackson2JsonRedisSerializer jsonRedisSerializer) {

        RedisCacheConfiguration config =
                RedisCacheConfiguration
                        .defaultCacheConfig()

                        .entryTtl(Duration.ofMinutes(30))

                        // Key 使用 String
                        .serializeKeysWith(
                                RedisSerializationContext
                                        .SerializationPair
                                        .fromSerializer(
                                                RedisSerializer.string()
                                        )
                        )

                        // Value 使用 JSON
                        .serializeValuesWith(
                                RedisSerializationContext
                                        .SerializationPair
                                        .fromSerializer(
                                                jsonRedisSerializer
                                        )
                        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
}
