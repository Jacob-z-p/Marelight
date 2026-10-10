package com.jacobzp.controller;


import com.jacobzp.dto.Result;
import com.jacobzp.service.IVoucherOrderService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥、jacobzp
 * @since 2021-12-22 ~ 2026-10-02
 */
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
//        // 版本1: 单线程安全,多线程不安全
//        return voucherOrderService.seckillVoucher1(voucherId);
//
//        // 版本2: 解决超卖问题
//        return voucherOrderService.seckillVoucher2(voucherId);


        // 版本3: 单机 synchronized 解决一人一单
//        return voucherOrderService.seckillVoucher3(voucherId);

        // 版本4: 自己实现的 Redis 分布式锁解决一人一单
//        return voucherOrderService.seckillVoucher4(voucherId);

        // 版本5: Redisson 分布式锁解决一人一单
        return voucherOrderService.seckillVoucher5(voucherId);
    }
}
