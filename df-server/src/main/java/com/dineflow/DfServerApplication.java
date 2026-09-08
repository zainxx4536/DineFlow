package com.dineflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.dineflow.mapper")
public class DfServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DfServerApplication.class, args);
    }

}
