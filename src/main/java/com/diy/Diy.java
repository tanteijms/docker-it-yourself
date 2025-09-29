package com.diy;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Docker It Yourself - Docker Registry HTTP API V2 实现
 * 
 * @author diy
 */
@SpringBootApplication
@MapperScan("com.diy.mapper")
@EnableScheduling
public class Diy {

    public static void main(String[] args) {
        SpringApplication.run(Diy.class, args);
    }

}
