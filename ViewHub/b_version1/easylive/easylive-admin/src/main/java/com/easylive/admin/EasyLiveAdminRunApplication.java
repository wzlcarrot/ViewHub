
package com.easylive.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(scanBasePackages = {"com.easylive"})
@MapperScan(basePackages = {"com.easylive.mappers"})
@EnableTransactionManagement   // 开启事务//就是后端的代码转化成事务性操作。。我喜欢你我们才能继续走下去。。如果中间闹矛盾离婚了。。我只能从头开始。。
@EnableScheduling  //用于控制任何定时任务。。也就是
/* 每5秒执行一次
    @Scheduled(fixedRate = 5000)
    public void reportCurrentTime() {
        System.out.println("当前时间: " + new Date());
    }
*
*
* */
public class EasyLiveAdminRunApplication {
    public static void main(String[] args) {
        SpringApplication.run(EasyLiveAdminRunApplication.class, args);
    }
}

