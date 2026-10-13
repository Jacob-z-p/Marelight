package com.jacobzp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jacobzp.dto.LoginFormDTO;
import com.jacobzp.dto.Result;
import com.jacobzp.dto.UserDTO;
import com.jacobzp.entity.User;
import com.jacobzp.mapper.UserMapper;
import com.jacobzp.service.IUserService;
import com.jacobzp.utils.MessageConstants;
import com.jacobzp.utils.RedisConstants;
import com.jacobzp.utils.RegexUtils;
import com.jacobzp.utils.SessionConstants;
import com.jacobzp.utils.SystemConstants;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author jacobzp
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送验证码并保存到session中:
     *  1.校验手机号
     *  2.随机生成验证码
     *  3.在session中存入验证码
     *  4.发送验证码
     */
    @Override
    public Result sendCodeBySession(String phone, HttpSession session) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail(MessageConstants.PHONE_INVALID);
        }
        // 2.随机生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 3.在session中存入验证码、过期时间和错误次数
        session.setAttribute(SessionConstants.LOGIN_CODE, code);
        session.setAttribute(SessionConstants.LOGIN_CODE_EXPIRE,
                System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(RedisConstants.LOGIN_CODE_TTL));
        session.setAttribute(SessionConstants.LOGIN_CODE_ERRORS, 0);
        // 4.发送验证码
        log.info("发送短信验证码成功, 验证码: {}", code);

        return Result.ok();
    }

    /**
     * 基于Session实现短信验证码登录:
     *  1.校验手机号
     *  2.基于session校验验证码的存在、过期时间和错误次数
     *  3.根据手机号查询用户
     *  4.判断用户是否存在
     *      如果不存在则创建新用户
     *  5.保存用户信息到session中
     *  6.结束
     */
    @Override
    public Result loginBySession(LoginFormDTO loginForm, HttpSession session) {
        // 1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail(MessageConstants.PHONE_INVALID);
        }

        /**
         * 2.基于Session校验验证码
         *  - 时间不能超过过期时间
         *  - 错误次数不能超过最大错误次数
         */
        Object cacheCode = session.getAttribute(SessionConstants.LOGIN_CODE);
        // session中未存入验证码
        if (cacheCode == null) {
            return Result.fail(MessageConstants.CODE_INVALID);
        }
        // 不能超过过期时间
        Long expireAt = (Long) session.getAttribute(SessionConstants.LOGIN_CODE_EXPIRE);
        if (expireAt == null || System.currentTimeMillis() > expireAt) {
            removeLoginCode(session);
            return Result.fail(MessageConstants.CODE_EXPIRED);
        }
        // 不能超过最大错误次数
        Integer errors = (Integer) session.getAttribute(SessionConstants.LOGIN_CODE_ERRORS);
        if (errors != null && errors >= SessionConstants.MAX_CODE_ERRORS) {
            removeLoginCode(session);
            return Result.fail(MessageConstants.CODE_ERROR_LIMIT);
        }
        // 验证码验证
        String code = loginForm.getCode();
        if (!cacheCode.equals(code)) {
            session.setAttribute(SessionConstants.LOGIN_CODE_ERRORS, (errors == null ? 0 : errors) + 1);
            return Result.fail(MessageConstants.CODE_INVALID);
        }

        // 3.到这里验证码验证成功, 删除验证码
        removeLoginCode(session);

        // 4.根据手机号查询用户
        User user = query().eq("phone", phone).one();

        // 5.判断用户是否存在
        if (user == null) {
            // 创建新用户
            user = createUserWithPhone(phone);
        }
        // 6.保存用户信息到session中
        //  BeanUtil.copyProperties(user, UserDTO.class): 将user赋值到UserDTO
        session.setAttribute(SessionConstants.LOGIN_USER, BeanUtil.copyProperties(user, UserDTO.class)); // hutool工具类

        return Result.ok();
    }



    /**
     * 发送验证码:
     *  1.校验手机号
     *  2.随机生成、存入并发送验证码
     */
    @Override
    public Result sendCodeByRedis(String phone) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail(MessageConstants.PHONE_INVALID);
        }
        // 2.随机生成6位验证码
        String code = RandomUtil.randomNumbers(6); // hutool工具类
        // 3.验证码存入redis，键带手机号，2分钟过期
        stringRedisTemplate.opsForValue().set( // redis类型: string
                RedisConstants.LOGIN_CODE_KEY + phone, // key -> login:code:手机号
                code, // value -> 验证码
                RedisConstants.LOGIN_CODE_TTL, // 有效期
                TimeUnit.MINUTES // 时间单位
        );
        // 4.发送验证码,用日志模拟发送过程
        log.debug("发送短信验证码成功，验证码：{}", code);
        // 5.返回成功结果
        return Result.ok();
    }

    /**
     * 登录:
     *  1.校验手机号和验证码
     *  2.根据手机号查询用户是否存在,如果不存在就创建用户
     *  3.将用户信息包装成UserDTO,并存储到redis中
     *  4.返回token
     */
    @Override
    public Result loginByRedis(LoginFormDTO loginForm) {
        // 1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail(MessageConstants.PHONE_INVALID);
        }
        // 2.从redis校验验证码，键按手机号区分
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone); // key -> login:code:手机号
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail(MessageConstants.CODE_INVALID);
        }
        // 3.根据手机号查询用户是否存在
        User user = query().eq("phone", phone).one(); // 使用mybatis-plus进行数据库查询
        // 4.如果用户不存在,则创建用户
        if (user == null) {
            user = createUserWithPhone(phone);
        }
        // 5.只把UserDTO写入redis，避免保存密码
        String token = UUID.randomUUID().toString(true); // 获取用户令牌
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token; // 用户的键
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class); // User转成UserDTO
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(), // hutool工具类
                CopyOptions.create()
                        .setIgnoreNullValue(true)   // 空值不进入hash
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString())); // 值转为String
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap); // 用户信息存入hash
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES); // 设置令牌时间
        // 6.验证码只用一次
        stringRedisTemplate.delete(RedisConstants.LOGIN_CODE_KEY + phone);
        // 7.返回token
        return Result.ok(token);
    }

    private void removeLoginCode(HttpSession session) {
        session.removeAttribute(SessionConstants.LOGIN_CODE);
        session.removeAttribute(SessionConstants.LOGIN_CODE_EXPIRE);
        session.removeAttribute(SessionConstants.LOGIN_CODE_ERRORS);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);// 添加手机号信息
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));// 添加昵称信息
        save(user);
        return user;
    }
}
