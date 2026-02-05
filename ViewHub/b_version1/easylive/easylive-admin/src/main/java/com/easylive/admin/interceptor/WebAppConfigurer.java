package com.easylive.admin.interceptor;

import com.easylive.entity.config.AppConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebAppConfigurer implements WebMvcConfigurer {

    private final AppInterceptor appInterceptor;

    private final AppConfig appConfig;

    //这个拦截器会对所有HTTP请求进行拦截处理。
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(appInterceptor).addPathPatterns("/**");
    }
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

    }

    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {

    }
}
