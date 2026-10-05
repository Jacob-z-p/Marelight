package com.jacobzp.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jacobzp.dto.Result;
import com.jacobzp.entity.Voucher;
import com.jacobzp.mapper.VoucherMapper;
import com.jacobzp.entity.SeckillVoucher;
import com.jacobzp.service.ISeckillVoucherService;
import com.jacobzp.service.IVoucherService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }

    /**
     * 添加秒杀券:
     * 1.首先添加到普通券中
     * 2.然后添加到秒杀券库存中
     *
     * 疑问:
     * 1.不用setCreateTime和setUpdateTime是不是因为mybatis-plus会自动添加?
     */
    @Override
    @Transactional // 因为要对多个数据库处理，所以加上事务管理
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);
    }
}
