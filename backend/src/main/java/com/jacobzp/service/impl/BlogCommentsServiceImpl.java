package com.jacobzp.service.impl;

import com.jacobzp.entity.BlogComments;
import com.jacobzp.mapper.BlogCommentsMapper;
import com.jacobzp.service.IBlogCommentsService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

}
