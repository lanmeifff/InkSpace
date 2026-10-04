package com.inkspace.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 把本地上传目录暴露为 /uploads/** 静态资源（生产环境改由 Nginx 直接托管）。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AppProperties appProperties;

    public WebMvcConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(appProperties.getUploadDir())
                .toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
