package com.jacobzp.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.jacobzp.dto.LoginFormDTO;
import com.jacobzp.dto.Result;
import com.jacobzp.entity.User;
import jakarta.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author jacobzp
 */
public interface IUserService extends IService<User> {

    Result sendCodeBySession(String phone, HttpSession session);

    Result sendCodeByRedis(String phone);

    Result loginBySession(LoginFormDTO loginForm, HttpSession session);

    Result loginByRedis(LoginFormDTO loginForm);

}
