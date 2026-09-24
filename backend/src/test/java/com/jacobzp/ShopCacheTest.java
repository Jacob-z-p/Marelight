package com.jacobzp;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.jacobzp.dto.Result;
import com.jacobzp.entity.Shop;
import com.jacobzp.service.IShopService;
import com.jacobzp.utils.RedisConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import jakarta.annotation.Resource;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(ShopSelectDelayConfig.class)
class ShopCacheTest {

    private static final Long SHOP_ID = 1L;
    private static final Long MISSING_ID = -1L;
    private static final int THREADS = 20;
    private static final int PER_THREAD = 200;
    private static final int BREAKDOWN_THREADS = 20;
    private static final long BREAKDOWN_DELAY_MS = 200L;

    @Resource
    private IShopService shopService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ShopSelectDelayInterceptor shopSelectDelayInterceptor;

    @Test
    void queryCachesHitAndSkipsMissingShop() {
        String key = RedisConstants.CACHE_SHOP_KEY + SHOP_ID;
        stringRedisTemplate.delete(key);

        Result first = shopService.queryById(SHOP_ID);
        assertTrue(first.getSuccess());
        Shop fromDb = (Shop) first.getData();
        assertNotNull(fromDb.getName());
        assertNotNull(stringRedisTemplate.opsForValue().get(key));

        Result second = shopService.queryById(SHOP_ID);
        assertTrue(second.getSuccess());
        Shop fromCache = (Shop) second.getData();
        assertEquals(fromDb.getName(), fromCache.getName());
        assertEquals(fromDb.getCreateTime(), fromCache.getCreateTime());
        assertEquals(fromDb.getUpdateTime(), fromCache.getUpdateTime());
        assertEquals(fromDb.getAvgPrice(), fromCache.getAvgPrice());

        Shop patch = new Shop();
        patch.setId(SHOP_ID);
        patch.setName(fromDb.getName());
        assertTrue(shopService.update(patch).getSuccess());
        assertNull(stringRedisTemplate.opsForValue().get(key));

        String missingKey = RedisConstants.CACHE_SHOP_KEY + MISSING_ID;
        stringRedisTemplate.delete(missingKey);
        shopSelectDelayInterceptor.reset();
        Result missing = shopService.queryById(MISSING_ID);
        assertFalse(missing.getSuccess());
        assertEquals("店铺不存在", missing.getErrorMsg());
        assertEquals("", stringRedisTemplate.opsForValue().get(missingKey));
        int selectsAfterFirstMiss = shopSelectDelayInterceptor.getSelectCount();
        Result missingAgain = shopService.queryById(MISSING_ID);
        assertFalse(missingAgain.getSuccess());
        assertEquals(selectsAfterFirstMiss, shopSelectDelayInterceptor.getSelectCount());

        Shop noId = new Shop();
        Result rejected = shopService.update(noId);
        assertFalse(rejected.getSuccess());
        assertEquals("店铺id不能为空", rejected.getErrorMsg());
    }

    /**
     * 删掉热点键后，20 个线程同时查询。selectById 被拖慢 200 毫秒，用来把击穿叠出来。
     */
    @Test
    void cacheBreakdownUnderConcurrentMiss() throws InterruptedException {
        ((Logger) LoggerFactory.getLogger("com.jacobzp")).setLevel(Level.WARN);
        String cacheKey = RedisConstants.CACHE_SHOP_KEY + SHOP_ID;
        String lockKey = RedisConstants.LOCK_SHOP_KEY + SHOP_ID;
        stringRedisTemplate.delete(cacheKey);
        stringRedisTemplate.delete(lockKey);
        shopSelectDelayInterceptor.reset();
        shopSelectDelayInterceptor.setDelayMs(BREAKDOWN_DELAY_MS);
        try {
            long[] latencies = new long[BREAKDOWN_THREADS];
            ExecutorService pool = Executors.newFixedThreadPool(BREAKDOWN_THREADS);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(BREAKDOWN_THREADS);
            AtomicInteger failures = new AtomicInteger();
            for (int i = 0; i < BREAKDOWN_THREADS; i++) {
                int index = i;
                pool.submit(() -> {
                    try {
                        start.await();
                        long begin = System.nanoTime();
                        Result result = shopService.queryById(SHOP_ID);
                        latencies[index] = System.nanoTime() - begin;
                        if (!Boolean.TRUE.equals(result.getSuccess()) || !(result.getData() instanceof Shop shop) || !SHOP_ID.equals(shop.getId())) {
                            failures.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failures.incrementAndGet();
                        throw new RuntimeException(e);
                    } finally {
                        done.countDown();
                    }
                });
            }
            long wallStart = System.nanoTime();
            start.countDown();
            done.await();
            long wallNanos = System.nanoTime() - wallStart;
            pool.shutdown();

            long[] samples = Arrays.copyOf(latencies, BREAKDOWN_THREADS);
            Arrays.sort(samples);
            long sum = 0;
            for (long sample : samples) {
                sum += sample;
            }
            int p99Index = Math.min(samples.length - 1, (int) Math.ceil(samples.length * 0.99) - 1);
            int selectById = shopSelectDelayInterceptor.getSelectCount();
            System.out.println("BREAKDOWN threads=" + BREAKDOWN_THREADS
                    + " selectById=" + selectById
                    + " wallMs=" + formatMs(wallNanos)
                    + " minMs=" + formatMs(samples[0])
                    + " avgMs=" + formatMs(sum / samples.length)
                    + " p99Ms=" + formatMs(samples[p99Index])
                    + " failures=" + failures.get());
            assertEquals(0, failures.get());
            assertEquals(1, selectById);
            assertNull(stringRedisTemplate.opsForValue().get(lockKey));
            assertNotNull(stringRedisTemplate.opsForValue().get(cacheKey));
        } finally {
            shopSelectDelayInterceptor.setDelayMs(0);
            stringRedisTemplate.delete(lockKey);
        }
    }

    /**
     * 默认 mvn test 不跑。需要对比时设置环境变量 SHOP_BENCHMARK=true。
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "SHOP_BENCHMARK", matches = "true")
    void benchmarkMysqlAgainstCacheHit() throws InterruptedException {
        // debug SQL 会把直查数据库的耗时打高，压测时只留警告
        ((Logger) LoggerFactory.getLogger("com.jacobzp")).setLevel(Level.WARN);
        String key = RedisConstants.CACHE_SHOP_KEY + SHOP_ID;
        for (int i = 0; i < 50; i++) {
            assertNotNull(shopService.getById(SHOP_ID));
        }
        stringRedisTemplate.delete(key);
        for (int i = 0; i < 50; i++) {
            assertTrue(shopService.queryById(SHOP_ID).getSuccess());
        }

        stringRedisTemplate.delete(key);
        long coldStart = System.nanoTime();
        Result cold = shopService.queryById(SHOP_ID);
        long coldNanos = System.nanoTime() - coldStart;
        assertTrue(cold.getSuccess());
        assertNotNull(stringRedisTemplate.opsForValue().get(key));

        Bench mysql = run(() -> assertNotNull(shopService.getById(SHOP_ID)));
        Bench cache = run(() -> assertTrue(shopService.queryById(SHOP_ID).getSuccess()));

        System.out.println("SHOP_BENCH threads=" + THREADS + " perThread=" + PER_THREAD);
        System.out.println("SHOP_BENCH coldMissMs=" + formatMs(coldNanos));
        System.out.println("SHOP_BENCH mysql " + mysql);
        System.out.println("SHOP_BENCH cacheHit " + cache);
    }

    private Bench run(Runnable task) throws InterruptedException {
        int total = THREADS * PER_THREAD;
        long[] latencies = new long[total];
        AtomicInteger index = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        for (int t = 0; t < THREADS; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < PER_THREAD; i++) {
                        long begin = System.nanoTime();
                        task.run();
                        latencies[index.getAndIncrement()] = System.nanoTime() - begin;
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    done.countDown();
                }
            });
        }
        long wallStart = System.nanoTime();
        start.countDown();
        done.await();
        long wallNanos = System.nanoTime() - wallStart;
        pool.shutdown();

        long[] samples = Arrays.copyOf(latencies, index.get());
        Arrays.sort(samples);
        long sum = 0;
        for (long sample : samples) {
            sum += sample;
        }
        int p99Index = Math.min(samples.length - 1, (int) Math.ceil(samples.length * 0.99) - 1);
        double seconds = wallNanos / 1_000_000_000.0;
        return new Bench(samples.length, sum / samples.length, samples[p99Index], samples.length / seconds);
    }

    private static String formatMs(long nanos) {
        return String.format("%.3f", nanos / 1_000_000.0);
    }

    private record Bench(int count, long avgNanos, long p99Nanos, double qps) {
        @Override
        public String toString() {
            return "count=" + count
                    + " avgMs=" + formatMs(avgNanos)
                    + " p99Ms=" + formatMs(p99Nanos)
                    + " qps=" + String.format("%.1f", qps);
        }
    }
}
