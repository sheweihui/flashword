package org.example.utils;

/**
 * 用户相关枚举
 */
public enum UserEnum {
    /** Redis 中用户 Token 的 key 前缀 — 完整 key: wf:session:{token} */
    USER_TOKEN("wf:session");

    private final String value;

    UserEnum(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
