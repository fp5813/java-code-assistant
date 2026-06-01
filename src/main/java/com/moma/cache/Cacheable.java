package com.moma.cache;

import java.lang.annotation.*;

/**
 * 标记方法结果可缓存。
 * 对应 Spring @Cacheable。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Cacheable {

    /** 缓存 key（支持 SpEL 风格占位符，如 #toolName:#argsHash） */
    String key();

    /** 过期时间（秒） */
    long ttl() default 300;

    /** 缓存名称/前缀 */
    String name() default "default";
}
