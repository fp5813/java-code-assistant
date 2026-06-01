package com.moma.di;

import java.lang.annotation.*;

/**
 * 标记需要 DI 容器执行依赖注入的字段或构造器。
 * 对应 Spring {@code @Autowired} / Jakarta {@code @Inject}。
 */
@Target({ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Inject {
}
