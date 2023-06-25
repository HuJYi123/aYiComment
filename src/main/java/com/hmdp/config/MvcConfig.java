package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshLoginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * className:MvcConfig
 * Package:com.hmdp.config
 * Description:一步一脚印！
 *
 * @Date: 2023/4/22 21:17
 * @Author:2692243932@qq.com
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {
//    @Autowired
//    private LoginInterceptor interceptor;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                "/shop/**",
                "/voucher/**",
                "/shop-type/**",
                "/upload/**",
                "/blog/hot",
                "/user/code",
                "/user/login"
        ).order(1);
        registry.addInterceptor(new RefreshLoginInterceptor(stringRedisTemplate))
                .addPathPatterns("/**").order(0); //数值越低，优先级越高
    }
}
