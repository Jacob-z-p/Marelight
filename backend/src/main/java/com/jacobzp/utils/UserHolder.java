package com.jacobzp.utils;

import com.jacobzp.dto.UserDTO;

/**
 * 线程存储用户信息
 */
public class UserHolder {
    // 一个线程池最多只存储一个UerDTO信息
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    // 存储用户信息
    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    // 获取用户信息
    public static UserDTO getUser(){
        return tl.get();
    }

    // 清理用户信息
    public static void removeUser(){
        tl.remove();
    }
}
