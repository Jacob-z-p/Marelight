package com.jacobzp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.json.JSONUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 缓存读写和互斥锁。业务上的成功或失败文案由调用方决定。
 */
@Component
public class CacheClient {

    private static final int MAX_LOCK_RETRIES = 10; // 锁的最大尝试次数
    private static final long LOCK_RETRY_INTERVAL_MS = 50L; // 等锁的睡眠时间
    private static final long TTL_JITTER_MINUTES = 5L;
    private static final String CACHE_NULL = "";
    private static final String UNLOCK_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """;

    private static final DefaultRedisScript<Long> UNLOCK = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 带互斥锁的查询。空字符串表示库里没有这条数据。
     * 没抢到锁时只等缓存或等锁过期，不绕过锁去查库。
     */
    public <R, ID> R queryWithMutex(
            String keyPrefix,
            String lockPrefix,
            ID id,
            Class<R> type,
            Function<ID, R> dbFallback,
            Long time,
            TimeUnit unit
    ) {
        String key = keyPrefix + id;
        String lockKey = lockPrefix + id;
        for (int pass = 0; pass < 2; pass++) {
            Hit<R> loaded = tryAcquire(key, lockKey, id, type, dbFallback, time, unit);
            if (loaded.found) {
                return loaded.value;
            }
            Hit<R> waited = waitWhileLocked(key, lockKey, type);
            if (waited.found) {
                return waited.value;
            }
        }
        Hit<R> cached = read(key, type);
        return cached.found ? cached.value : null;
    }

    public void delete(String key) {
        stringRedisTemplate.delete(key);
    }

    private <R, ID> Hit<R> tryAcquire(
            String key,
            String lockKey,
            ID id,
            Class<R> type,
            Function<ID, R> dbFallback,
            Long time,
            TimeUnit unit
    ) {
        for (int i = 0; i < MAX_LOCK_RETRIES; i++) {
            Hit<R> cached = read(key, type);
            if (cached.found) {
                return cached;
            }
            String token = UUID.randomUUID().toString(true);
            if (!tryLock(lockKey, token)) {
                sleepQuietly();
                continue;
            }
            try {
                cached = read(key, type);
                if (cached.found) {
                    return cached;
                }
                return Hit.hit(loadAndCache(key, id, dbFallback, time, unit));
            } finally {
                unlock(lockKey, token);
            }
        }
        return Hit.miss();
    }

    private <R> Hit<R> waitWhileLocked(String key, String lockKey, Class<R> type) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(RedisConstants.LOCK_SHOP_TTL);
        while (System.nanoTime() < deadline) {
            Hit<R> cached = read(key, type);
            if (cached.found) {
                return cached;
            }
            if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey))) {
                return Hit.miss();
            }
            sleepQuietly();
        }
        return read(key, type);
    }

    private <R> Hit<R> read(String key, Class<R> type) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null) {
            return Hit.miss();
        }
        if (json.isEmpty()) {
            return Hit.hit(null);
        }
        return Hit.hit(JSONUtil.toBean(json, type));
    }

    private <R, ID> R loadAndCache(String key, ID id, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        R value = dbFallback.apply(id);
        if (value == null) {
            stringRedisTemplate.opsForValue().set(key, CACHE_NULL, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        long seconds = unit.toSeconds(time) + ThreadLocalRandom.current().nextLong(TTL_JITTER_MINUTES * 60 + 1);
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), seconds, TimeUnit.SECONDS);
        return value;
    }

    private boolean tryLock(String key, String token) {
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(
                key,
                token,
                RedisConstants.LOCK_SHOP_TTL,
                TimeUnit.SECONDS
        );
        return Boolean.TRUE.equals(locked);
    }

    private void unlock(String key, String token) {
        stringRedisTemplate.execute(UNLOCK, Collections.singletonList(key), token);
    }

    /**
     * 休眠
     */
    private void sleepQuietly() {
        try {
            Thread.sleep(LOCK_RETRY_INTERVAL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class Hit<R> {
        private final boolean found;
        private final R value;

        private Hit(boolean found, R value) {
            this.found = found;
            this.value = value;
        }

        private static <R> Hit<R> miss() {
            return new Hit<>(false, null);
        }

        private static <R> Hit<R> hit(R value) {
            return new Hit<>(true, value);
        }
    }
}
