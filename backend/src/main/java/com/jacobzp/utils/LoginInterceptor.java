package com.jacobzp.utils;

import com.jacobzp.dto.UserDTO;
import com.jacobzp.entity.User;
import jakarta.servlet.http.HttpSession;
import org.jspecify.annotations.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 第二层拦截器
 */
public class LoginInterceptor implements HandlerInterceptor {

//    /**
//     * 拦截前: 只放行线程中存有用户信息的请求
//     *  用户信息存储在session中
//     */
//    @Override
//    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
//        // 1.从session中获取用户信息
//        HttpSession session = request.getSession();
//        UserDTO user = (UserDTO) session.getAttribute(SessionConstants.LOGIN_USER);
//        // 2.判断用户是否存在
//        if (user == null) {
//            // 不存在则拦截，并返回 401 状态码
//            response.setStatus(401);
//            return false;
//        }
//        // 3.保存用户信息到ThreadLocal中
//        UserHolder.saveUser(user);
//        // 否则放行
//        return true;
//    }

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


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        // 移除用户
        UserHolder.removeUser();
    }
}
