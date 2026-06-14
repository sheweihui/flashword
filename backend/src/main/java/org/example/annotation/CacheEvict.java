package org.example.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@Documented
/**
 * 缓存清除注解 - 标记需要清除缓存的方法
 */
public @interface CacheEvict {
    String key();
    boolean allEntries() default false;
}
