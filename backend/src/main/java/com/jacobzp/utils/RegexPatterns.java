package com.jacobzp.utils;

/**
 * @author 虎哥
 */
public abstract class RegexPatterns {
    /**
     * 中国大陆手机号：共 11 位，以 1 开头，第二位为 3–9，其余为数字。
     * 只校验格式，不按运营商号段列举。号段会持续新增（如 192、191、195、172），
     * 白名单会漏掉新号。物联网号多为 13 位，不在本规则内。
     */
    public static final String PHONE_REGEX = "^1[3-9]\\d{9}$";
    /**
     * 邮箱正则
     */
    public static final String EMAIL_REGEX = "^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$";
    /**
     * 密码正则。4~32位的字母、数字、下划线
     */
    public static final String PASSWORD_REGEX = "^\\w{4,32}$";
    /**
     * 验证码正则, 6位数字或字母
     */
    public static final String VERIFY_CODE_REGEX = "^[a-zA-Z\\d]{6}$";

}
