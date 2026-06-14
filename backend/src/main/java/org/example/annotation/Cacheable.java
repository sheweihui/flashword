package org.example.annotation;

import java.lang.annotation.*;

/**
 * 缓存注解 - 用于标记需要缓存的方法
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Cacheable {

    /**
     * 缓存的key表达式（支持SpEL）
     * 例如: "'user:' + #userId + ':books'"
     */
    String key();

    /**
     * 缓存过期时间（秒）
     * 默认3600秒（1小时）
     */
    long expire() default 3600;

    /**
     * 是否使用JSON序列化
     */
    boolean useJson() default true;
}
