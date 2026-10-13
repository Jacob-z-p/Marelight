package com.jacobzp.controller;


import com.jacobzp.dto.LoginFormDTO;
import com.jacobzp.dto.Result;
import com.jacobzp.dto.UserDTO;
import com.jacobzp.entity.UserInfo;
import cn.hutool.core.util.StrUtil;
import com.jacobzp.service.IUserInfoService;
import com.jacobzp.service.IUserService;
import com.jacobzp.utils.RedisConstants;
import com.jacobzp.utils.SessionConstants;
import com.jacobzp.utils.UserHolder;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author jacobzp
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 基于session发送手机验证码
     */
     @PostMapping("code")
    public Result sendCodeBySession(@RequestParam("phone") String phone, HttpSession session) {
        // 调用service层接口实现“校验、保存并发送短信验证码”
        return userService.sendCodeBySession(phone, session);
    }

    /**
     * 基于Redis发送手机验证码
     */
//    @PostMapping("code")
    public Result sendCodeByRedis(@RequestParam("phone") String phone) {
        // 调用service层接口实现“校验、保存并发送短信验证码”
        return userService.sendCodeByRedis(phone);
    }


    /**
     * 基于session实现登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result loginBySession(@RequestBody LoginFormDTO loginForm, HttpSession session){
        return userService.loginBySession(loginForm, session);
    }


    /**
     * 基于Redis实现登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
//    @PostMapping("/login")
    public Result loginByRedis(@RequestBody LoginFormDTO loginForm){
        return userService.loginByRedis(loginForm);
    }

    /**
     * 基于session实现登出功能
     */
    @PostMapping("/logout")
    public Result logout(HttpSession session) {
        session.removeAttribute(SessionConstants.LOGIN_USER);
        return Result.ok();
    }

    /**
     * 基于redis实现登出功能
     */
//    @PostMapping("/logout")
    public Result logout(HttpServletRequest request){
        // 1.删除redis中的登录用户
        String token = request.getHeader("authorization");
        if (StrUtil.isNotBlank(token)) {
            stringRedisTemplate.delete(RedisConstants.LOGIN_USER_KEY + token);
        }
        // 2.从线程中删除用户信息 -- 拦截器结束时也会清理，多删一次没有问题
        UserHolder.removeUser();
        // 3.返回成功结果
        return Result.ok();
    }

    @GetMapping("/me")
    public Result me(){
        // 1.从线程中获取用户信息
        UserDTO user = UserHolder.getUser();
        // 2.返回成功结果
        return Result.ok(user);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }
}
