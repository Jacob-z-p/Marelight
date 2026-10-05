package com.jacobzp.service.impl;

import com.jacobzp.dto.Result;
import com.jacobzp.entity.SeckillVoucher;
import com.jacobzp.entity.VoucherOrder;
import com.jacobzp.mapper.VoucherOrderMapper;
import com.jacobzp.service.ISeckillVoucherService;
import com.jacobzp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jacobzp.utils.RedisIdWorker;
import com.jacobzp.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;

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
            return Result.fail("秒杀尚未开始");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 抢购时间结束
            return Result.fail("秒杀已经结束");
        }

        // 3.判断库存是否充足
        Integer stock = voucher.getStock();
        if (stock < 1) {
            // 库存不足
            return Result.fail("库存不足");
        }

        // 4.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .update();
        if (!success) {
            // 可能也是库存不足导致的
            return Result.fail("库存不足");
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
            return Result.fail("秒杀尚未开始");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 抢购时间结束
            return Result.fail("秒杀已经结束");
        }

        // 3.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0) // 在这里解决超卖问题
                .update();
        if (!success) {
            // 可能也是库存不足导致的
            return Result.fail("库存不足");
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
            return Result.fail("秒杀尚未开始");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 抢购时间结束
            return Result.fail("秒杀已经结束");
        }

        // 3.判断库存是否充足
        Integer stock = voucher.getStock();
        if (stock < 1) {
            // 库存不足
            return Result.fail("库存不足");
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
            return Result.fail("用户已购买");
        }
        // 6.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0) // 在这里解决超卖问题
                .update();
        if (!success) {
            // 可能也是库存不足导致的
            return Result.fail("库存不足");
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
