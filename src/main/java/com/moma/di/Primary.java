package com.moma.di;

import java.lang.annotation.*;

/**
 * 当多个相同类型的 Bean 存在时，标记优先注入的 Bean。
 * 对应 Spring {@code @Primary}。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Primary {
}
