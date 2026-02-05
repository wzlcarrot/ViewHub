package com.easylive.web.annotation;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

//第一个参数的意思是只能作用方法上，第二个参数的意思是，可以作用到其他注解或者类上。。定义注解的时候最重要的两个注解，，第一个是Target,第二个是Retention
@Target({ElementType.METHOD,ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface GlobalInterceptor {

    //大部分的操作不需要登录
    boolean checkLogin() default false;
}
