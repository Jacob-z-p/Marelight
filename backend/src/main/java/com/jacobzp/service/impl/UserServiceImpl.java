package com.jacobzp.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.jacobzp.entity.User;
import com.jacobzp.mapper.UserMapper;
import com.jacobzp.service.IUserService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

}
