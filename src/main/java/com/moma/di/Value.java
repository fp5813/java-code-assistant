package com.moma.di;

import java.lang.annotation.*;

/**
 * 注入配置值。支持格式：{@code @Value("${key:default}")}。
 * 对应 Spring {@code @Value}。
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Value {

    /** 占位符表达式，如 "${redis.host:localhost}" */
    String value();
}
