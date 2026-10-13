package com.jacobzp.dto;

import lombok.Data;

/**
 * 登录会话。loginAt 只写入 Redis，不进入 UserHolder。
 */
@Data
public class LoginSession {
    private UserDTO user;
    private long loginAt;
}
