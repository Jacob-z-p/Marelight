package com.jacobzp.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 1L;   // 验证码有效期:1分钟
    public static final String LOGIN_CODE_INTERVAL_KEY = "login:code:interval:";
    public static final Long LOGIN_CODE_INTERVAL_TTL = 60L; // 发送间隔，单位秒
    public static final String LOGIN_CODE_PHONE_COUNT_KEY = "login:code:count:phone:";
    public static final String LOGIN_CODE_IP_COUNT_KEY = "login:code:count:ip:";
    public static final Long LOGIN_CODE_COUNT_TTL = 86400L; // 每日计数窗口，单位秒
    public static final int LOGIN_CODE_PHONE_LIMIT = 10;
    public static final int LOGIN_CODE_IP_LIMIT = 20;
    public static final String LOGIN_CODE_MARK_KEY = "login:code:mark:";
    public static final Long LOGIN_CODE_MARK_TTL = 5L; // 发送标记保留时间，单位分钟
    public static final String LOGIN_CODE_ERRORS_KEY = "login:code:errors:";
    public static final String LOGIN_USER_KEY = "login:token:";
    /** 滑动过期，单位分钟（5小时）。只允许与 TimeUnit.MINUTES 一起使用。 */
    public static final Long LOGIN_USER_TTL = 300L;
    /** 绝对上限，单位天。滑动续期不能超过登录时刻加上这个时长。 */
    public static final Long LOGIN_USER_ABSOLUTE_TTL = 7L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;
    public static final Long LOCK_ORDER_TTL = 1200L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String SECKILL_ORDER_KEY = "seckill:order:";
    public static final String SECKILL_INFO_KEY = "seckill:info:";
    public static final String SECKILL_STREAM_KEY = "stream.orders";
    public static final String SECKILL_STREAM_GROUP = "g1";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
}
