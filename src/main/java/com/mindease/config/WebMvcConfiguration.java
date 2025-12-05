package com.mindease.config;

import com.mindease.interceptor.JwtTokenInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 配置类，注册web层相关组件
 */
@Configuration
@Slf4j
public class WebMvcConfiguration implements WebMvcConfigurer {

    @Autowired
    private JwtTokenInterceptor jwtTokenInterceptor;

    /**
     * 注册自定义拦截器
     *
     * @param registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        log.info("开始注册自定义拦截器...");
        registry.addInterceptor(jwtTokenInterceptor)
                .addPathPatterns("/auth/profile", "/counselor/**", "/appointment/**", "/user/**", "/admin/**", "/assessment/**", "/mood/**")  // 需要 token 的路径
                .excludePathPatterns("/auth/register", "/auth/login", "/appointment/available-slots", "/assessment/scales", "/assessment/scale/**");  // 不需要 token 的路径
    }
}