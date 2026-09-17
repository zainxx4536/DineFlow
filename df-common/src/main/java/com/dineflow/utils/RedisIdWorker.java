package com.dineflow.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 全局唯一ID生成器，可以作为订单号
 */
@Component
@RequiredArgsConstructor
public class RedisIdWorker {

    /**
     * 自定义起始时间：2026-01-01 00:00:00
     * 自定义起始时间的 Unix 时间戳：1767225600L
     */
    private static final long BEGIN_TIMESTAMP = 1767225600L;

    /**
     * 序列号占用位数
     */
    private static final int COUNT_BITS = 32;

    private final StringRedisTemplate redisTemplate;

    public long nextId(String keyPrefix) {

        // 1. 获取当前时间
        LocalDateTime now = LocalDateTime.now();

        // 2. 获取当前时间的秒级时间戳
        long nowSecond = now.toEpochSecond(ZoneOffset.of("+8"));

        // 3. 计算相对于起始时间的时间戳
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 4. Redis自增
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // Redis Key 示例： dineflow:icr:order:2026:09:16
        // increment 进行存储数据自增
        // 更重要的是：Redis 的 INCR 是原子操作，这也是可以拿来做序列号的主要原因。
        Long count = redisTemplate.opsForValue().increment(
                "dineflow:icr:" + keyPrefix + ":" + date
        );

        // 位运算需要确保拆箱后 count != null
        if (count == null) {
            throw new IllegalStateException("Redis ID 自增失败");
        }
        //将时间戳与Redis生成的序列号使用'与运算'进行拼接
        return timestamp << COUNT_BITS | count;
    }
}