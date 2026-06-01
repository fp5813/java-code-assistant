package com.moma.di;

import java.lang.annotation.*;

/**
 * 标记在方法上，在 Bean 初始化完成后执行。
 * 对应 Spring {@code @PostConstruct}。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PostConstruct {
}
