package com.jacobzp.service;

import com.jacobzp.dto.Result;
import com.jacobzp.entity.VoucherOrder;
import com.baomidou.mybatisplus.spring.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥、jacobzp
 * @since 2021-12-22 ~ 2026-10-02
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 购买优惠券-实现1:
     *  只支持单线程:
     *      存在线程安全问题,会出现超卖的风险
     */
    Result seckillVoucher1(Long voucherId);

    /**
     * 购买优惠券-实现2
     *  解决线程安全的超卖问题 -- 守住业务的底线
     */
    Result seckillVoucher2(Long voucherId);

    /**
     * 购买优惠券-实现3
     *  在线程安全的前提下,实现1人1单的业务逻辑
     */
    Result seckillVoucher3(Long voucherId);

    /**
     * 购买优惠券-实现4
     *  用分布式锁实现一人一单，多台实例下仍然生效
     */
    Result seckillVoucher4(Long voucherId);

    /**
     * 购买优惠券-实现5
     *  用 Redisson 分布式锁实现一人一单
     */
    Result seckillVoucher5(Long voucherId);

    /**
     * 创建订单
     */
    Result createVoucher(Long voucherId);
}
