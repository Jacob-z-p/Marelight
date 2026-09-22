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
import com.jacobzp.utils.RedisConstants;
import com.jacobzp.utils.RegexUtils;
import com.jacobzp.utils.SystemConstants;
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
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送验证码:
     *  1.校验验证码
     *  2.随机生成、存入并发送验证码
     */
    @Override
    public Result sendCode(String phone) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        // 2.随机生成6位验证码
        String code = RandomUtil.randomNumbers(6);
        // 3.验证码存入redis，键带手机号，2分钟过期
        stringRedisTemplate.opsForValue().set(
                RedisConstants.LOGIN_CODE_KEY + phone,
                code,
                RedisConstants.LOGIN_CODE_TTL,
                TimeUnit.MINUTES
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
    public Result login(LoginFormDTO loginForm) {
        // 1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }
        // 2.从redis校验验证码，键按手机号区分
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
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
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)   // 空值不进入hash
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString())); // 值转为String
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap); // 用户信息存入hash
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS); // 设置令牌时间
        // 6.验证码只用一次
        stringRedisTemplate.delete(RedisConstants.LOGIN_CODE_KEY + phone);
        // 7.返回token
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);// 添加手机号信息
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));// 添加昵称信息
        save(user);
        return user;
    }
}
