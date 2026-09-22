package com.jacobzp.controller;


import com.jacobzp.dto.LoginFormDTO;
import com.jacobzp.dto.Result;
import com.jacobzp.dto.UserDTO;
import com.jacobzp.entity.UserInfo;
import com.jacobzp.service.IUserInfoService;
import com.jacobzp.service.IUserService;
import com.jacobzp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 发送手机验证码
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        return userService.sendCode(phone, session);
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        return userService.login(loginForm, session);
    }

    /**
     * 登出功能
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(HttpSession session){
        // 1.从session中删除用户信息
        session.removeAttribute("user");
        // 2.从线程中删除用户信息 -- 其实在拦截器中已经有关于线程清理的操作了,但是多删除一次也没有问题
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
