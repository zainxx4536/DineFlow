package com.dineflow.config;

import com.dineflow.interceptor.AdminRequestInterceptor;
import com.dineflow.json.JacksonObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * WebMvc 配置类
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfiguration implements WebMvcConfigurer {

    private final AdminRequestInterceptor adminRequestInterceptor;

    /**
     * 将自定义的 JacksonObjectMapper 注册到 Spring MVC 的消息转换器中
     *
     * @param converters the list of configured converters to be extended
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {

        // 创建 Jackson 消息转换器
        MappingJackson2HttpMessageConverter converter =
                new MappingJackson2HttpMessageConverter();

        // 使用自定义 ObjectMapper
        converter.setObjectMapper(new JacksonObjectMapper());

        // 放到最前面，提高优先级
        converters.add(0, converter);
    }

    /**
     * 注册拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminRequestInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/admin/employee/login");
    }
}
