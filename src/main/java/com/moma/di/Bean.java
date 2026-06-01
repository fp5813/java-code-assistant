package com.moma.di;

import java.lang.annotation.*;

/**
 * 标记在 {@link Configuration} 类的方法上，声明一个 Bean 定义。
 * 方法返回值即 Bean 实例。
 * 对应 Spring {@code @Bean}。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Bean {

    /** Bean 名称（默认使用方法名） */
    String name() default "";

    /** 作用域：singleton / prototype */
    String scope() default "singleton";
}
