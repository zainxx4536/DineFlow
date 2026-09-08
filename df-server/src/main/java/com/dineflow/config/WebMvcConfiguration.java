package com.dineflow.config;

import com.dineflow.json.JacksonObjectMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * WebMvc 配置类
 */
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

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
}
