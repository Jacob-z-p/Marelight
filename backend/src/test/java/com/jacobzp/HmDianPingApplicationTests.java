package com.jacobzp;

import com.jacobzp.utils.RedisIdWorker;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@SpringBootTest
class HmDianPingApplicationTests {

    // 常量:一个线程池中线程的数量
    private static final int threadPoolNumber = 500;

    @Resource
    private RedisIdWorker redisIdWorker;

    // 创建简单的线程池
    private ExecutorService es = Executors.newFixedThreadPool(threadPoolNumber);

    /**
     * 测试全局ID生成器
     */
    @Test
    void testIdWorker() throws InterruptedException {

        CountDownLatch latch = new CountDownLatch(300);

        // 设置一个线程要完成的任务
        //   通过lambda表达式实现Runnable的run方法
        // 任务:生成100个订单号
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };

        long begin = System.currentTimeMillis();

        // 线程池中的线程抢这300个任务
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }

        latch.await();

        long end = System.currentTimeMillis();

        System.out.println("time = " + (end - begin));

    }

}
