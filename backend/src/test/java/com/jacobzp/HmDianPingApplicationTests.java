package com.jacobzp;

import com.jacobzp.utils.RedisIdWorker;
import jakarta.annotation.Resource;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private RedisIdWorker redisIdWorker;

    /**
     * 测试全局ID生成器
     */
    void testIdWorker() {

    }

}
