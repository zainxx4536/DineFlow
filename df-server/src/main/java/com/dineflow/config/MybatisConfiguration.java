package com.dineflow.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mybatis-Plus 的分页插件配置类,分页拦截器
 */
@Configuration
public class MybatisConfiguration {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(){
        //创建拦截器
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        //创建分页插件
        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        //进行一些分页配置（可选）
        paginationInnerInterceptor.setMaxLimit(1000L);
        //添加分页插件进拦截器
        interceptor.addInnerInterceptor(paginationInnerInterceptor);
        //返回拦截器
        return interceptor;
    }
}
