package com.jacobzp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jacobzp.dto.LoginFormDTO;
import com.jacobzp.dto.LoginSession;
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
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.security.SecureRandom;
import java.util.List;
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

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final long SEND_OK = 0L;
    private static final long SEND_FREQUENT = 1L;
    private static final long VERIFY_OK = 1L;
    private static final long VERIFY_MISSING = -1L;
    private static final long VERIFY_LIMIT = -2L;

    /**
     * 发送频控。通过才写入间隔和每日计数。
     * 返回 0 表示允许发送，1 表示间隔未到，2 表示手机号或 IP 达到每日上限。
     * KEYS[1] login:code:interval:{phone}，发送间隔标记
     * KEYS[2] login:code:count:phone:{phone}，该手机号 24 小时内的发送次数
     * KEYS[3] login:code:count:ip:{ip}，该 IP 24 小时内的发送次数
     * ARGV[1] 发送间隔，单位秒
     * ARGV[2] 手机号每日上限
     * ARGV[3] IP 每日上限
     * ARGV[4] 每日计数的过期时间，单位秒
     */
    private static final DefaultRedisScript<Long> SEND_LIMIT_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then
                return 1
            end
            local phone = tonumber(redis.call('GET', KEYS[2]) or '0')
            local ip = tonumber(redis.call('GET', KEYS[3]) or '0')
            if phone >= tonumber(ARGV[2]) or ip >= tonumber(ARGV[3]) then
                return 2
            end
            redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])
            local phoneCount = redis.call('INCR', KEYS[2])
            if phoneCount == 1 then
                redis.call('EXPIRE', KEYS[2], ARGV[4])
            end
            local ipCount = redis.call('INCR', KEYS[3])
            if ipCount == 1 then
                redis.call('EXPIRE', KEYS[3], ARGV[4])
            end
            return 0
            """, Long.class);

    /**
     * 原子校验验证码。通过或次数用尽时删除验证码、错误次数和发送标记。
     * 返回 1 表示校验通过，0 表示验证码错误，-1 表示验证码不存在，-2 表示错误次数用尽。
     * KEYS[1] login:code:{phone}，验证码哈希
     * KEYS[2] login:code:errors:{phone}，该验证码已错误次数
     * KEYS[3] login:code:mark:{phone}，发送标记
     * ARGV[1] 用户提交验证码的哈希
     * ARGV[2] 最大错误次数
     * ARGV[3] 错误次数的过期时间，单位秒
     */
    private static final DefaultRedisScript<Long> VERIFY_CODE_SCRIPT = new DefaultRedisScript<>("""
            local stored = redis.call('GET', KEYS[1])
            if not stored then
                return -1
            end
            if stored ~= ARGV[1] then
                local errors = redis.call('INCR', KEYS[2])
                if errors == 1 then
                    redis.call('EXPIRE', KEYS[2], ARGV[3])
                end
                if errors >= tonumber(ARGV[2]) then
                    redis.call('DEL', KEYS[1], KEYS[2], KEYS[3])
                    return -2
                end
                return 0
            end
            redis.call('DEL', KEYS[1], KEYS[2], KEYS[3])
            return 1
            """, Long.class);

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
     *  2.校验发送间隔、手机号和 IP 的每日上限
     *  3.生成验证码，Redis 存哈希，日志保留明文
     */
    @Override
    public Result sendCodeByRedis(String phone, String clientIp) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail(MessageConstants.PHONE_INVALID);
        }
        // 2.间隔未到不扣每日次数；达到上限直接返回
        Long limit = stringRedisTemplate.execute(
                SEND_LIMIT_SCRIPT,
                List.of(
                        RedisConstants.LOGIN_CODE_INTERVAL_KEY + phone,
                        RedisConstants.LOGIN_CODE_PHONE_COUNT_KEY + phone,
                        RedisConstants.LOGIN_CODE_IP_COUNT_KEY + clientIp
                ),
                String.valueOf(RedisConstants.LOGIN_CODE_INTERVAL_TTL),
                String.valueOf(RedisConstants.LOGIN_CODE_PHONE_LIMIT),
                String.valueOf(RedisConstants.LOGIN_CODE_IP_LIMIT),
                String.valueOf(RedisConstants.LOGIN_CODE_COUNT_TTL)
        );
        if (limit == null || limit != SEND_OK) {
            if (limit != null && limit == SEND_FREQUENT) {
                return Result.fail(MessageConstants.CODE_SEND_FREQUENT);
            }
            return Result.fail(MessageConstants.CODE_SEND_LIMIT);
        }
        // 3.生成验证码。Redis 只存哈希，明文只打到日志里便于测试
        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        stringRedisTemplate.opsForValue().set(
                RedisConstants.LOGIN_CODE_KEY + phone,
                DigestUtil.sha256Hex(code),
                RedisConstants.LOGIN_CODE_TTL,
                TimeUnit.MINUTES
        );
        // 标记验证码已生成
        stringRedisTemplate.opsForValue().set(
                RedisConstants.LOGIN_CODE_MARK_KEY + phone,
                "1",
                RedisConstants.LOGIN_CODE_MARK_TTL,
                TimeUnit.MINUTES
        );
        stringRedisTemplate.delete(RedisConstants.LOGIN_CODE_ERRORS_KEY + phone);
        log.debug("发送短信验证码成功，验证码：{}", code);
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
        // 2.原子校验验证码。比对的是哈希，通过后立刻删除
        String code = loginForm.getCode();
        if (StrUtil.isBlank(code)) {
            return Result.fail(MessageConstants.CODE_INVALID);
        }
        Long verified = stringRedisTemplate.execute(
                VERIFY_CODE_SCRIPT,
                List.of(
                        RedisConstants.LOGIN_CODE_KEY + phone,
                        RedisConstants.LOGIN_CODE_ERRORS_KEY + phone,
                        RedisConstants.LOGIN_CODE_MARK_KEY + phone
                ),
                DigestUtil.sha256Hex(code),
                String.valueOf(SessionConstants.MAX_CODE_ERRORS),
                String.valueOf(TimeUnit.MINUTES.toSeconds(RedisConstants.LOGIN_CODE_TTL))
        );
        if (verified == null || verified != VERIFY_OK) {
            if (verified != null && verified == VERIFY_LIMIT) {
                return Result.fail(MessageConstants.CODE_ERROR_LIMIT);
            }
            if (verified != null && verified == VERIFY_MISSING
                    && Boolean.TRUE.equals(stringRedisTemplate.hasKey(RedisConstants.LOGIN_CODE_MARK_KEY + phone))) {
                return Result.fail(MessageConstants.CODE_EXPIRED);
            }
            return Result.fail(MessageConstants.CODE_INVALID);
        }
        // 3.根据手机号查询用户是否存在
        User user = query().eq("phone", phone).one(); // 使用mybatis-plus进行数据库查询
        // 4.如果用户不存在,则创建用户
        if (user == null) {
            user = createUserWithPhone(phone);
        }
        // 5.只把用户信息和登录时间写入redis，避免保存密码。SET 与过期时间一次完成
        String token = UUID.randomUUID().toString(true); // 获取用户令牌
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token; // 用户的键
        LoginSession session = new LoginSession();
        session.setUser(BeanUtil.copyProperties(user, UserDTO.class));
        session.setLoginAt(System.currentTimeMillis());
        stringRedisTemplate.opsForValue().set(
                tokenKey,
                JSONUtil.toJsonStr(session),
                RedisConstants.LOGIN_USER_TTL,
                TimeUnit.MINUTES
        );
        // 6.返回token
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
