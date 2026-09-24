package com.jacobzp;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 只在击穿测试里打开。给店铺主键查询加延迟并计数，用来把并发未命中叠在同一次查库上。
 */
@Intercepts({
        @Signature(
                type = Executor.class,
                method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}
        )
})
public class ShopSelectDelayInterceptor implements Interceptor {

    private static final String SHOP_SELECT_BY_ID = "com.jacobzp.mapper.ShopMapper.selectById";

    private final AtomicInteger selectCount = new AtomicInteger();
    private volatile long delayMs;

    public void reset() {
        selectCount.set(0);
    }

    public void setDelayMs(long delayMs) {
        this.delayMs = delayMs;
    }

    public int getSelectCount() {
        return selectCount.get();
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
        if (SHOP_SELECT_BY_ID.equals(statement.getId())) {
            selectCount.incrementAndGet();
            if (delayMs > 0) {
                Thread.sleep(delayMs);
            }
        }
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }
}
