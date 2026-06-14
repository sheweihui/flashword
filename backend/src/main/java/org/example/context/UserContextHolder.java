package org.example.context;

import org.example.entity.UserContext;

/**
 * 用户上下文持有者
 * 通过 ThreadLocal 存储当前请求的用户上下文，在拦截器中设置，在 Controller/Service 中读取
 * 请求结束时必须调用 clear() 清理，避免内存泄漏
 */
public class UserContextHolder {

    private static final ThreadLocal<UserContext> USER_CONTEXT_HOLDER = new ThreadLocal<>();

    /** 保存当前用户上下文 */
    public static void set(UserContext context) {
        USER_CONTEXT_HOLDER.set(context);
    }

    /** 获取当前用户上下文 */
    public static UserContext get() {
        return USER_CONTEXT_HOLDER.get();
    }

    /** 获取当前用户 ID */
    public static Long getUserId() {
        UserContext context = get();
        return context != null ? context.getUserId() : null;
    }

    /** 请求结束后必须清理，否则 ThreadLocal 会导致内存泄漏 */
    public static void clear() {
        USER_CONTEXT_HOLDER.remove();
    }
}
