package com.dineflow.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2WebMvc;

/**
 * Knife4j 配置类
 */
@Configuration
@EnableSwagger2WebMvc
public class Knife4jConfiguration {

    @Bean
    public Docket adminDocker() {
        //指定使用 Swagger2 规范
        return new Docket(DocumentationType.SWAGGER_2)
                .apiInfo(new ApiInfoBuilder()
                        //描述字段支持 Markdown 语法
                        .description("# DineFlow API")
                        .version("1.0")
                        .build())
                //分组名称
                .groupName("管理端接口")
                .select()
                //这里指定 Controller 扫描包路径
                .apis(RequestHandlerSelectors.basePackage("com.dineflow.controller.admin"))
                .paths(PathSelectors.any())
                .build();
    }

    @Bean
    public Docket userDocker() {
        //指定使用 Swagger2 规范
        return new Docket(DocumentationType.SWAGGER_2)
                .apiInfo(new ApiInfoBuilder()
                        //描述字段支持 Markdown 语法
                        .description("# DineFlow API")
                        .version("1.0")
                        .build())
                //分组名称
                .groupName("用户端接口")
                .select()
                //这里指定 Controller 扫描包路径
                .apis(RequestHandlerSelectors.basePackage("com.dineflow.controller.user"))
                .paths(PathSelectors.any())
                .build();
    }
}
