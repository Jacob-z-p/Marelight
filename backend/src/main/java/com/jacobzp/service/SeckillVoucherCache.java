package com.jacobzp.service;

import com.jacobzp.entity.SeckillVoucher;
import com.jacobzp.entity.Voucher;
import com.jacobzp.utils.RedisConstants;
import jakarta.annotation.Resource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 把秒杀券的库存和起止时间放进 Redis。库存键已存在时不覆盖。
 */
@Component
public class SeckillVoucherCache implements ApplicationRunner {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Override
    public void run(ApplicationArguments args) {
        List<SeckillVoucher> vouchers = seckillVoucherService.list();
        for (SeckillVoucher voucher : vouchers) {
            cache(voucher.getVoucherId(), voucher.getStock(), voucher.getBeginTime().toString(), voucher.getEndTime().toString());
        }
    }

    public void cache(Voucher voucher) {
        cache(voucher.getId(), voucher.getStock(), voucher.getBeginTime().toString(), voucher.getEndTime().toString());
    }

    private void cache(Long voucherId, Integer stock, String beginTime, String endTime) {
        stringRedisTemplate.opsForValue().setIfAbsent(
                RedisConstants.SECKILL_STOCK_KEY + voucherId,
                String.valueOf(stock)
        );
        Map<String, String> info = new HashMap<>();
        info.put("beginTime", beginTime);
        info.put("endTime", endTime);
        stringRedisTemplate.opsForHash().putAll(RedisConstants.SECKILL_INFO_KEY + voucherId, info);
    }
}
