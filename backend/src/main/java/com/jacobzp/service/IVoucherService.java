package com.jacobzp.service;

import com.jacobzp.dto.Result;
import com.jacobzp.entity.Voucher;
import com.baomidou.mybatisplus.spring.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥、jacobzp
 * @since 2021-12-22 ~ 2026-10-01
 */
public interface IVoucherService extends IService<Voucher> {

    Result queryVoucherOfShop(Long shopId);

    /**
     * 添加秒杀券
     */
    void addSeckillVoucher(Voucher voucher);
}
