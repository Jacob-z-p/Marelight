package com.jacobzp.utils;

import com.jacobzp.dto.UserDTO;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public class LoginInterceptor implements HandlerInterceptor {

    /**
     * 请求拦截前:
     *   1.获取session中的"user"信息
     *   2.判断用户是否存在:
     *      ·如果不存在,返回401
     *      ·如果存在,线程存储用户信息并放行
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 获取session全部信息
        HttpSession session = request.getSession();
        // 获取键为"user"的session信息
        Object user = session.getAttribute("user");
        // 如果用户不存在:返回 401
        //  401: 请求没有通过身份信息,服务器拒绝处理
        if (user == null) {
            response.setStatus(401);
            return false;
        }
        // 线程存储用户信息
        UserHolder.saveUser((UserDTO) user);
        // 拦截放行
        return true;
    }

    /**
     * 请求结束回到拦截器时:
     *  清理线程用户信息, 维护线程干净与安全
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 清理线程用户信息
        UserHolder.removeUser();
    }
}
