package com.moma.di;

import java.lang.annotation.*;

/**
 * 标记一个类为配置类，其 {@link Bean} 方法将被容器处理。
 * 对应 Spring {@code @Configuration}。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Configuration {
}
