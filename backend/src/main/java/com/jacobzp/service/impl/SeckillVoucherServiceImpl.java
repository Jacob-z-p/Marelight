package com.jacobzp.service.impl;

import com.jacobzp.entity.SeckillVoucher;
import com.jacobzp.mapper.SeckillVoucherMapper;
import com.jacobzp.service.ISeckillVoucherService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-01-04
 */
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {

}
