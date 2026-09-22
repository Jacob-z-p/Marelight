package com.jacobzp.utils;

import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 第二层拦截器
 */
public class LoginInterceptor implements HandlerInterceptor {

    /**
     * 拦截前: 只放行线程中存有用户信息的请求
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 如果线程中没有用户信息,则返回401
        if (UserHolder.getUser() == null) {
            response.setStatus(401);
            return false;
        }
        // 否则放行
        return true;
    }
}
