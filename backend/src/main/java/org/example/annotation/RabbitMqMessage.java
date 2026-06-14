package org.example.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@Documented
/**
 * RabbitMQ消息注解 - 标记需要发送消息的方法
 */
public @interface RabbitMqMessage {
    String message() default "";
}
