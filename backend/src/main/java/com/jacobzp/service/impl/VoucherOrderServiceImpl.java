package com.jacobzp.service.impl;

import com.jacobzp.dto.Result;
import com.jacobzp.entity.SeckillVoucher;
import com.jacobzp.entity.VoucherOrder;
import com.jacobzp.mapper.VoucherOrderMapper;
import com.jacobzp.service.ISeckillVoucherService;
import com.jacobzp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jacobzp.utils.MessageConstants;
import com.jacobzp.utils.RedisConstants;
import com.jacobzp.utils.RedisIdWorker;
import com.jacobzp.utils.SimpleRedisLock;
import com.jacobzp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥、jacobzp
 * @since 2021-12-22 ~ 2026-10-02
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT = new DefaultRedisScript<>("""
            local stock = tonumber(redis.call('get', KEYS[1]))
            if stock == nil or stock <= 0 then
                return 1
            end
            if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then
                return 2
            end
            redis.call('incrby', KEYS[1], -1)
            redis.call('sadd', KEYS[2], ARGV[1])
            return 0
            """, Long.class);

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    /**
     * 购买优惠券-实现1:
     *  只支持单线程:
     *      在并发测试下, 存在线程安全问题, 会出现超卖的风险
     *      原因:并发下没有加锁，可能在减库存之前，多个线程同时查了库存
     */
    @Override
    @Transactional // tb_seckill_voucher、tb_voucher_order 两张表
    public Result seckillVoucher1(Long voucherId) {

        // 1.根据优惠券id查询优惠券信息 -- seckill_voucher的id与voucher的id是共享的
        //  MVCC: 快照读
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        // 2.判断秒杀是否开始和结束
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 抢购时间还没开始
            return Result.fail(MessageConstants.SECKILL_NOT_START);
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 抢购时间结束
            return Result.fail(MessageConstants.SECKILL_ENDED);
        }

        // 3.判断库存是否充足
        Integer stock = voucher.getStock();
        if (stock < 1) {
            // 库存不足
            return Result.fail(MessageConstants.STOCK_NOT_ENOUGH);
        }

        // 4.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .update();
        if (!success) {
            // 可能也是库存不足导致的
            return Result.fail(MessageConstants.STOCK_NOT_ENOUGH);
        }

        // 5.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 5.1 订单id
        long orderId = redisIdWorker.nextId("seckillOrder");
        voucherOrder.setId(orderId);
        // 5.2 用户id
        Long userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);
        // 5.3 代金券id
        voucherOrder.setVoucherId(voucherId);
        // 5.4 订单写入数据库
        save(voucherOrder);

        // 6.返回订单id
        return Result.ok(orderId);
    }

    /**
     * 购买优惠券-实现2
     *  解决线程安全的超卖问题 -- 守住业务的底线
     *      本质上就是在update时通过where查看库存是否>0
     */
    @Override
    @Transactional // tb_seckill_voucher、tb_voucher_order 两张表
    public Result seckillVoucher2(Long voucherId) {

        // 1.根据优惠券id查询优惠券信息 -- seckill_voucher的id与voucher的id是共享的
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        // 2.判断秒杀是否开始和结束
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 抢购时间还没开始
            return Result.fail(MessageConstants.SECKILL_NOT_START);
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 抢购时间结束
            return Result.fail(MessageConstants.SECKILL_ENDED);
        }

        // 3.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0) // 在这里解决超卖问题
                .update();
        if (!success) {
            // 可能也是库存不足导致的
            return Result.fail(MessageConstants.STOCK_NOT_ENOUGH);
        }

        // 4.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 4.1 订单id
        long orderId = redisIdWorker.nextId("seckillOrder");
        voucherOrder.setId(orderId);
        // 4.2 用户id
        Long userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);
        // 4.3 代金券id
        voucherOrder.setVoucherId(voucherId);
        // 4.4 订单写入数据库
        save(voucherOrder);

        // 5.返回订单id
        return Result.ok(orderId);
    }



    /**
     * 购买优惠券-实现3
     *  在线程安全的前提下,实现1人1单的业务逻辑
     */
    @Override
    public Result seckillVoucher3(Long voucherId) {

        // 1.根据优惠券id查询优惠券信息 -- seckill_voucher的id与voucher的id是共享的
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        // 2.判断秒杀是否开始和结束
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 抢购时间还没开始
            return Result.fail(MessageConstants.SECKILL_NOT_START);
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 抢购时间结束
            return Result.fail(MessageConstants.SECKILL_ENDED);
        }

        // 3.判断库存是否充足
        Integer stock = voucher.getStock();
        if (stock < 1) {
            // 库存不足
            return Result.fail(MessageConstants.STOCK_NOT_ENOUGH);
        }

        Long userId = UserHolder.getUser().getId();

        /**
         * 这里值得好好讲一下:
         *  1.为什么要在这里加锁?
         *      因为要等createVoucher这个事务提交完成后才能释放锁,所以要把整个方法包起来
         *  2.为什么锁对象是userId?
         *      因为我们希望的不是所有线程抢这把锁,而是同一个userId来抢这一把锁 -- 本质上就是在业务实现的前提下尽可能把锁范围减小
         *  3.为什么要获取代理对象?
         *      因为在这里是用this调用createVoucher函数，会导致事务失效,所以要手动获取代理对象
         */
        synchronized (userId.toString().intern()) {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucher(voucherId);
        }
    }

    /**
     * 购买优惠券-实现4
     *  一人一单改用 Redis 分布式锁，多台实例抢的是同一把锁
     */
    @Override
    public Result seckillVoucher4(Long voucherId) {

        // 1.根据优惠券id查询优惠券信息 -- seckill_voucher的id与voucher的id是共享的
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        // 2.判断秒杀是否开始和结束
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 抢购时间还没开始
            return Result.fail(MessageConstants.SECKILL_NOT_START);
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 抢购时间结束
            return Result.fail(MessageConstants.SECKILL_ENDED);
        }

        // 3.判断库存是否充足
        Integer stock = voucher.getStock();
        if (stock < 1) {
            // 库存不足
            return Result.fail(MessageConstants.STOCK_NOT_ENOUGH);
        }

        Long userId = UserHolder.getUser().getId();
        // 锁名只带用户：同一个用户才互斥，不同用户可以同时下单。键实际是 lock:order:{userId}
        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        boolean locked = lock.tryLock(RedisConstants.LOCK_ORDER_TTL);
        if (!locked) {
            return Result.fail(MessageConstants.REPEAT_ORDER);
        }
        try {
            // 走代理，等 createVoucher 的事务提交后再放锁
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucher(voucherId);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 购买优惠券-实现5
     *  一人一单改用 Redisson。没抢到直接返回，抢到后看门狗会给锁续期
     */
    @Override
    public Result seckillVoucher5(Long voucherId) {

        // 1.根据优惠券id查询优惠券信息 -- seckill_voucher的id与voucher的id是共享的
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        // 2.判断秒杀是否开始和结束
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 抢购时间还没开始
            return Result.fail(MessageConstants.SECKILL_NOT_START);
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 抢购时间结束
            return Result.fail(MessageConstants.SECKILL_ENDED);
        }

        // 3.判断库存是否充足
        Integer stock = voucher.getStock();
        if (stock < 1) {
            // 库存不足
            return Result.fail(MessageConstants.STOCK_NOT_ENOUGH);
        }

        Long userId = UserHolder.getUser().getId();
        // 同一个用户才互斥，不同用户可以同时下单
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean locked = lock.tryLock();
        if (!locked) {
            return Result.fail(MessageConstants.REPEAT_ORDER);
        }
        try {
            // 走代理，等 createVoucher 的事务提交后再放锁
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucher(voucherId);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 购买优惠券-实现6
     *  资格判断在 Redis 里完成，订单写入队列后立即返回
     */
    @Override
    public Result seckillVoucher6(Long voucherId) {
        Map<Object, Object> info = stringRedisTemplate.opsForHash()
                .entries(RedisConstants.SECKILL_INFO_KEY + voucherId);
        if (info.isEmpty()) {
            return Result.fail(MessageConstants.SECKILL_VOUCHER_NOT_FOUND);
        }
        LocalDateTime beginTime = LocalDateTime.parse(info.get("beginTime").toString());
        LocalDateTime endTime = LocalDateTime.parse(info.get("endTime").toString());
        if (beginTime.isAfter(LocalDateTime.now())) {
            return Result.fail(MessageConstants.SECKILL_NOT_START);
        }
        if (endTime.isBefore(LocalDateTime.now())) {
            return Result.fail(MessageConstants.SECKILL_ENDED);
        }

        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                List.of(RedisConstants.SECKILL_STOCK_KEY + voucherId, RedisConstants.SECKILL_ORDER_KEY + voucherId),
                userId.toString()
        );
        if (result == null || result == 1) {
            return Result.fail(MessageConstants.STOCK_NOT_ENOUGH);
        }
        if (result == 2) {
            return Result.fail(MessageConstants.ALREADY_PURCHASED);
        }

        long orderId = redisIdWorker.nextId("seckillOrder");
        Map<String, String> message = new HashMap<>();
        message.put("id", String.valueOf(orderId));
        message.put("userId", userId.toString());
        message.put("voucherId", voucherId.toString());
        stringRedisTemplate.opsForStream().add(
                StreamRecords.mapBacked(message).withStreamKey(RedisConstants.SECKILL_STREAM_KEY)
        );
        return Result.ok(orderId);
    }

    @Override
    @Transactional
    public void handleVoucherOrder(Long orderId, Long userId, Long voucherId) {
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            throw new IllegalStateException("库存不足");
        }
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
    }

    @Transactional // tb_seckill_voucher、tb_voucher_order 两张表
    public Result createVoucher(Long voucherId) {
        // -------------- 一人一单业务 -----------------

        // 4.根据优惠券id和用户id查询订单
        // 4.1 用户id
        Long userId = UserHolder.getUser().getId();
        // 4.2 查询订单
        Long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();

        // 5.判断订单是否存在
        if (count > 0) {
            // 用户已经购买过了
            return Result.fail(MessageConstants.ALREADY_PURCHASED);
        }
        // 6.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0) // 在这里解决超卖问题
                .update();
        if (!success) {
            // 可能也是库存不足导致的
            return Result.fail(MessageConstants.STOCK_NOT_ENOUGH);
        }

        // 6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 6.1 订单id
        long orderId = redisIdWorker.nextId("seckillOrder");
        voucherOrder.setId(orderId);
        // 6.2 用户id
        voucherOrder.setUserId(userId);
        // 6.3 代金券id
        voucherOrder.setVoucherId(voucherId);
        // 6.4 订单写入数据库
        save(voucherOrder);

        // 7.返回订单id
        return Result.ok(orderId);
    }
}
