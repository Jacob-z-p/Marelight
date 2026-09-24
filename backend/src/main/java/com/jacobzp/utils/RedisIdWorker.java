package com.jacobzp.utils;


import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 全局ID生成器
 */
@Component
public class RedisIdWorker {

    // 常量: 定义开始的时间戳 -- 2026年09月24日0:0:0
    private static final long BEGIN_TIMESTAMP = 1790208000;
    // 常量: 时间戳左移位数 -- 本质上也是序列号的位数
    private static final int COUNT_BITS = 32;

    @Resource // redis类型注入
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 生成ID
     */
    public long nextId(String keyPrefix) {
        // 1.生成时间戳
        LocalDateTime now = LocalDateTime.now(); // 生成当前时间
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC); // 根据当前时区生成long类型的值
        long timestamp = nowSecond - BEGIN_TIMESTAMP; // 拿到时间戳

        // 2.生成序列号
        //  在redis中通过INCR作为计数器生成序列号
        //  期望:每一天重置序列号,避免超过 2^32 的上限
        //       所以,每一天都要更新键->键包含日期
        // 2.1获取当前日期,精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2获取自增长的序列号
        Long count = stringRedisTemplate.opsForValue().increment("inc:" + keyPrefix + ":" + date);

        // 3.拼接并返回
        return timestamp << COUNT_BITS | count;
    }

//    public static void main(String[] args) {
//        // 生成当前的时间
//        LocalDateTime localDateTime = LocalDateTime.of(2026, 9, 24, 0, 0, 0);
//        long second = localDateTime.toEpochSecond(ZoneOffset.UTC);
//        System.out.println(second); //1790208000
//    }

}
