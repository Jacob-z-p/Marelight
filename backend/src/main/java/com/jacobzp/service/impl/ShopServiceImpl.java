package com.jacobzp.service.impl;

import com.jacobzp.dto.Result;
import com.jacobzp.entity.Shop;
import com.jacobzp.mapper.ShopMapper;
import com.jacobzp.service.IShopService;
import com.jacobzp.utils.CacheClient;
import com.jacobzp.utils.RedisConstants;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private CacheClient cacheClient;

    @Override
    public boolean save(Shop entity) {
        boolean saved = super.save(entity);
        if (saved && entity.getId() != null) {
            cacheClient.delete(RedisConstants.CACHE_SHOP_KEY + entity.getId());
        }
        return saved;
    }

    /**
     * 根据id查询店铺信息。缓存和互斥锁在 CacheClient 里。
     */
    @Override
    public Result queryById(Long id) {
        // 1.根据id查找店铺信息,如果失败就返回
        Shop shop = cacheClient.queryWithMutex(
                RedisConstants.CACHE_SHOP_KEY,
                RedisConstants.LOCK_SHOP_KEY,
                id,
                Shop.class,
                this::getById,
                RedisConstants.CACHE_SHOP_TTL,
                TimeUnit.MINUTES
        );
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        // 2.返回成功信息
        return Result.ok(shop);
    }

    /**
     * 更新店铺信息:
     *  1.先更新数据库
     *  2.再删除缓存
     */
    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 1.先更新数据库
        updateById(shop);
        // 2.再删除缓存，避免数据库和缓存各写一次对不上
        cacheClient.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
