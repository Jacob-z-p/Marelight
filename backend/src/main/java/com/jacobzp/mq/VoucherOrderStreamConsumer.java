package com.jacobzp.mq;

import com.jacobzp.service.IVoucherOrderService;
import com.jacobzp.utils.RedisConstants;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 从 Redis Stream 取出秒杀订单，写入数据库。
 */
@Component
public class VoucherOrderStreamConsumer {

    private static final String CONSUMER = "c1";

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IVoucherOrderService voucherOrderService;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private Thread worker;

    @PostConstruct
    public void start() {
        ensureGroup();
        worker = new Thread(this::loop, "seckill-order-consumer");
        worker.setDaemon(true);
        worker.start();
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        worker.interrupt();
    }

    private void ensureGroup() {
        try {
            stringRedisTemplate.opsForStream().createGroup(
                    RedisConstants.SECKILL_STREAM_KEY,
                    ReadOffset.from("0"),
                    RedisConstants.SECKILL_STREAM_GROUP
            );
        } catch (Exception ignored) {
            // 组和流已存在时再创建会失败
        }
    }

    private void loop() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                        Consumer.from(RedisConstants.SECKILL_STREAM_GROUP, CONSUMER),
                        StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                        StreamOffset.create(RedisConstants.SECKILL_STREAM_KEY, ReadOffset.lastConsumed())
                );
                if (records == null || records.isEmpty()) {
                    continue;
                }
                for (MapRecord<String, Object, Object> record : records) {
                    handle(record);
                }
            } catch (Exception e) {
                if (!running.get()) {
                    return;
                }
                ensureGroup();
            }
        }
    }

    private void handle(MapRecord<String, Object, Object> record) {
        try {
            Map<Object, Object> value = record.getValue();
            Long orderId = Long.valueOf(value.get("id").toString());
            Long userId = Long.valueOf(value.get("userId").toString());
            Long voucherId = Long.valueOf(value.get("voucherId").toString());
            voucherOrderService.handleVoucherOrder(orderId, userId, voucherId);
            stringRedisTemplate.opsForStream().acknowledge(
                    RedisConstants.SECKILL_STREAM_KEY,
                    RedisConstants.SECKILL_STREAM_GROUP,
                    record.getId()
            );
        } catch (Exception e) {
            // 不确认消息，留在待处理列表里
            org.slf4j.LoggerFactory.getLogger(VoucherOrderStreamConsumer.class)
                    .error("秒杀订单落库失败，消息留在待处理列表: {}", record.getId(), e);
        }
    }
}
