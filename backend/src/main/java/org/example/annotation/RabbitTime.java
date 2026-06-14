package org.example.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@Documented
/**
 * RabbitMQ耗时监控注解 - 标记需要记录耗时的RabbitMQ消费者方法
 */
public @interface RabbitTime {
    String message();
}
