package com.jacobzp.dto;

import lombok.Data;

@Data
public class UserDTO {
    private Long id; // 用户主键id
    private String nickName; // 用户昵称
    private String icon; // 用户图片
}
