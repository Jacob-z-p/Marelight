package com.jacobzp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.jacobzp.dto.LoginSession;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;

/**
 * 第一层拦截器
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 拦截前:
     *  1.从浏览器中拿到token
     *  2.根据token从redis中拿到用户信息
     *  3.超过绝对上限则删除token
     *  4.将用户信息存入线程中
     *  5.剩余时间不足三分之一时再续期，且不超过绝对上限
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 1.从浏览器中拿取 token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            return true;
        }
        // 2.从redis拿取用户信息。旧的Hash会话类型不匹配时删掉，让用户重新登录
        String key = RedisConstants.LOGIN_USER_KEY + token;
        String json = readSession(key);
        if (StrUtil.isBlank(json)) {
            return true;
        }
        LoginSession session = JSONUtil.toBean(json, LoginSession.class);
        if (session == null || session.getUser() == null) {
            return true;
        }
        // 3.超过绝对上限则删除token
        long now = System.currentTimeMillis();
        if (now - session.getLoginAt() >= TimeUnit.DAYS.toMillis(RedisConstants.LOGIN_USER_ABSOLUTE_TTL)) {
            stringRedisTemplate.delete(key);
            return true;
        }
        // 4.将用户信息存入线程中
        UserHolder.saveUser(session.getUser());
        // 5.剩余时间不足三分之一时再续期
        refreshIfNeeded(key, now, session.getLoginAt());
        return true;
    }

    private String readSession(String key) {
        try {
            return stringRedisTemplate.opsForValue().get(key);
        } catch (RedisSystemException e) {
            if (!isWrongType(e)) {
                throw e;
            }
            stringRedisTemplate.delete(key);
            return null;
        }
    }

    private void refreshIfNeeded(String key, long now, long loginAt) {
        long slidingSeconds = TimeUnit.MINUTES.toSeconds(RedisConstants.LOGIN_USER_TTL);
        Long remain = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (remain != null && remain > slidingSeconds / 3) {
            return;
        }
        long absoluteRemainSeconds = TimeUnit.DAYS.toSeconds(RedisConstants.LOGIN_USER_ABSOLUTE_TTL)
                - TimeUnit.MILLISECONDS.toSeconds(now - loginAt);
        if (absoluteRemainSeconds <= 0) {
            stringRedisTemplate.delete(key);
            return;
        }
        stringRedisTemplate.expire(key, Math.min(slidingSeconds, absoluteRemainSeconds), TimeUnit.SECONDS);
    }

    private boolean isWrongType(Throwable e) {
        while (e != null) {
            String message = e.getMessage();
            if (message != null && message.contains("WRONGTYPE")) {
                return true;
            }
            e = e.getCause();
        }
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserHolder.removeUser();
    }
}
