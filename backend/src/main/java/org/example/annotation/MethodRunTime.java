package org.example.annotation;

import java.lang.annotation.*;

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
/**
 * 方法耗时监控注解 - 标记需要记录执行时间的方法
 */
public @interface MethodRunTime {
    String value() default "";
}
