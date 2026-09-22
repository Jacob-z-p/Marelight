package com.jacobzp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jacobzp.dto.LoginFormDTO;
import com.jacobzp.dto.Result;
import com.jacobzp.dto.UserDTO;
import com.jacobzp.entity.User;
import com.jacobzp.mapper.UserMapper;
import com.jacobzp.service.IUserService;
import com.jacobzp.utils.RegexUtils;
import com.jacobzp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpSession;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    /**
     * 发送验证码:
     *  1.校验验证码
     *  2.随机生成、存入并发送验证码
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        // 2.随机生成6位验证码
        String code = RandomUtil.randomNumbers(6);
        // 3.验证码存入session中
        session.setAttribute("code", code);
        // 4.发送验证码,用日志模拟发送过程
        log.debug("发送短信验证码成功，验证码：{}", code);
        // 5.返回成功结果
        return Result.ok();
    }

    /**
     * 登录:
     *  1.校验手机号和验证码
     *  2.根据手机号查询用户是否存在,如果不存在就创建用户
     *  3.将用户信息包装成UserDTO,并存储到session中
     *  4.返回成功结果
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        // 2.校验验证码
        Object cacheCode = session.getAttribute("code");
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.toString().equals(code)) {
            return Result.fail("验证码错误");
        }
        // 3.根据手机号查询用户是否存在
        User user = query().eq("phone", phone).one(); // 使用mybatis-plus进行数据库查询
        // 4.如果用户不存在,则创建用户
        if (user == null) {
            user = createUserWithPhone(phone);
        }
        // 5.session中只存储 UserDTO, 保证不泄露隐私信息
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        session.setAttribute("user", userDTO);
        // 6.返回成功结果
        return Result.ok(session.getId());
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);// 添加手机号信息
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));// 添加昵称信息
        save(user);
        return user;
    }
}
