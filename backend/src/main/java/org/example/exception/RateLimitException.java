package org.example.exception;

/**
 * 限流异常 — 当请求被接口限流拦截时抛出
 * <p>
 * 由 {@link GlobalExceptionHandler} 统一捕获，返回友好提示。
 */
public class RateLimitException extends RuntimeException {

    /** 限流类型：用户级 */
    public static final String TYPE_USER = "USER";
    /** 限流类型：全局限流 */
    public static final String TYPE_GLOBAL = "GLOBAL";

    private final String type;

    /**
     * @param type    限流类型（{@link #TYPE_USER} / {@link #TYPE_GLOBAL}）
     * @param message 用户可见的提示信息
     */
    public RateLimitException(String type, String message) {
        super(message);
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
