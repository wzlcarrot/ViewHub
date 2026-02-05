
package com.easylive.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(scanBasePackages = {"com.easylive"})
@MapperScan(basePackages = {"com.easylive.mappers"})
@EnableScheduling
@EnableTransactionManagement   // 开启事务//就是后端的代码转化成事务性操作。。我喜欢你我们才能继续走下去。。如果中间闹矛盾离婚了。。我只能从头开始。。
public class EasyLiveWebRunApplication {
    public static void main(String[] args) {
        SpringApplication.run(EasyLiveWebRunApplication.class, args);
    }
}

