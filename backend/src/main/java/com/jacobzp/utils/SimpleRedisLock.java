package com.jacobzp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的分布式锁。每次使用按业务名 new 一个，不是全局单例。
 */
public class SimpleRedisLock implements ILock {

    private static final String KEY_PREFIX = "lock:";
    /**
     *  值前缀：
     *      static final 类第一个被加载时算一次，之后这个 JVM 里所有 SimpleRedisLock 都共用这一个 UUID
     *      每个进程一份，避免不同机器上相同的线程号删掉对方的锁
     */
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    /**
     * lua脚本：
     *  释放锁之前先判断该锁是不是自己抢的锁
     *      如果是，则释放锁
     *      如果不是，则不做任何处理
     */
    private static final String UNLOCK_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """;

    private static final DefaultRedisScript<Long> UNLOCK = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);

    // 业务名
    private final String name;

    private final StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 尝试获取锁
     */
    @Override
    public boolean tryLock(Long timeoutSec) {
        String threadId = ID_PREFIX + Thread.currentThread().threadId();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(
                KEY_PREFIX + name,
                threadId,
                timeoutSec,
                TimeUnit.SECONDS
        );
        return Boolean.TRUE.equals(locked);
    }

    /**
     * 释放锁
     */
    @Override
    public void unlock() {
        String threadId = ID_PREFIX + Thread.currentThread().threadId();
        // 执行lua脚本
        stringRedisTemplate.execute(UNLOCK, Collections.singletonList(KEY_PREFIX + name), threadId);
    }
}
