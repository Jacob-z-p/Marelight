package com.jacobzp.config;

import com.jacobzp.utils.LoginInterceptor;
import com.jacobzp.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.annotation.Resource;

/**
 * <p>
 * 拦截器配置类
 * </p>
 *
 * @author jacobzp
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 基于session实现拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/user/code",
                        "/user/login",
                        "/blog/hot",
                        "/shop/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/voucher/**"
                );
    }

//    @Override
//    public void addInterceptors(InterceptorRegistry registry) {
//        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
//                .addPathPatterns("/**")
//                .order(0);
//        registry.addInterceptor(new LoginInterceptor())
//                .excludePathPatterns(
//                        "/user/code",
//                        "/user/login",
//                        "/blog/hot",
//                        "/shop/**",
//                        "/shop-type/**",
//                        "/upload/**",
//                        "/voucher/**"
//                )
//                .order(1);
//    }
}
