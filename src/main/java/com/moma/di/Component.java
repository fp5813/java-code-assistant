package com.moma.di;

import java.lang.annotation.*;

/**
 * 标记一个类为组件，由 DI 容器管理。
 * 对应 Spring {@code @Component}。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Component {

    /** Bean 名称（默认使用类名首字母小写） */
    String name() default "";

    /** 作用域：singleton / prototype */
    String scope() default "singleton";
}
