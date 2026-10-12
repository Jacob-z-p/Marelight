package com.jacobzp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jacobzp.dto.Result;
import com.jacobzp.dto.UserDTO;
import com.jacobzp.entity.User;
import com.jacobzp.entity.Voucher;
import com.jacobzp.entity.VoucherOrder;
import com.jacobzp.service.ISeckillVoucherService;
import com.jacobzp.service.IUserService;
import com.jacobzp.service.IVoucherOrderService;
import com.jacobzp.service.IVoucherService;
import com.jacobzp.utils.RedisConstants;
import com.jacobzp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SeckillAsyncCompareTest {

    private static final int STOCK = 20;
    private static final int USERS = 50;

    @Resource
    private IVoucherService voucherService;
    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void compareSyncAndAsync() throws Exception {
        Voucher voucher = new Voucher();
        voucher.setShopId(1L);
        voucher.setTitle("async-compare");
        voucher.setPayValue(100L);
        voucher.setActualValue(200L);
        voucher.setType(1);
        voucher.setStatus(1);
        voucher.setStock(STOCK);
        voucher.setBeginTime(LocalDateTime.now().minusMinutes(1));
        voucher.setEndTime(LocalDateTime.now().plusHours(1));
        voucherService.addSeckillVoucherAndCache(voucher);
        Long voucherId = voucher.getId();
        List<UserDTO> users = loadUsers();

        Run sync = run(voucherId, users, voucherOrderService::seckillVoucher5);
        int syncOrders = countOrders(voucherId);

        reset(voucherId);
        Run async = run(voucherId, users, voucherOrderService::seckillVoucher6);
        int asyncOrders = waitOrders(voucherId, async.success);

        System.out.println("COMPARE_RESULT syncMs=" + sync.millis
                + " syncSuccess=" + sync.success
                + " syncOrders=" + syncOrders
                + " asyncMs=" + async.millis
                + " asyncSuccess=" + async.success
                + " asyncOrders=" + asyncOrders);

        assertEquals(STOCK, sync.success);
        assertEquals(sync.success, syncOrders);
        assertEquals(STOCK, async.success);
        assertEquals(async.success, asyncOrders);
        assertTrue(async.millis < sync.millis);
    }

    private Run run(Long voucherId, List<UserDTO> users, LongFunction<Result> buy) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(users.size());
        CountDownLatch ready = new CountDownLatch(users.size());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(users.size());
        AtomicInteger success = new AtomicInteger();
        for (UserDTO user : users) {
            pool.submit(() -> {
                UserHolder.saveUser(user);
                ready.countDown();
                try {
                    start.await();
                    Result result = buy.apply(voucherId);
                    if (Boolean.TRUE.equals(result.getSuccess())) {
                        success.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    UserHolder.removeUser();
                    done.countDown();
                }
            });
        }
        ready.await();
        long begin = System.nanoTime();
        start.countDown();
        done.await();
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin);
        pool.shutdown();
        return new Run(millis, success.get());
    }

    private void reset(Long voucherId) {
        voucherOrderService.remove(new LambdaQueryWrapper<VoucherOrder>().eq(VoucherOrder::getVoucherId, voucherId));
        seckillVoucherService.update()
                .set("stock", STOCK)
                .eq("voucher_id", voucherId)
                .update();
        stringRedisTemplate.opsForValue().set(RedisConstants.SECKILL_STOCK_KEY + voucherId, String.valueOf(STOCK));
        stringRedisTemplate.delete(RedisConstants.SECKILL_ORDER_KEY + voucherId);
    }

    private int waitOrders(Long voucherId, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15000;
        int rows = 0;
        while (System.currentTimeMillis() < deadline) {
            rows = countOrders(voucherId);
            if (rows >= expected) {
                return rows;
            }
            Thread.sleep(200);
        }
        return rows;
    }

    private int countOrders(Long voucherId) {
        Long count = voucherOrderService.lambdaQuery().eq(VoucherOrder::getVoucherId, voucherId).count();
        return count == null ? 0 : count.intValue();
    }

    private List<UserDTO> loadUsers() {
        List<UserDTO> users = new ArrayList<>();
        for (int i = 0; i < USERS; i++) {
            String phone = String.valueOf(13700100001L + i);
            User user = userService.query().eq("phone", phone).one();
            if (user == null) {
                user = new User();
                user.setPhone(phone);
                user.setNickName("cmp" + i);
                userService.save(user);
            }
            UserDTO dto = new UserDTO();
            dto.setId(user.getId());
            dto.setNickName(user.getNickName());
            users.add(dto);
        }
        return users;
    }

    private record Run(long millis, int success) {
    }
}
