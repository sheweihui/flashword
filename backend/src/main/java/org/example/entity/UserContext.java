package org.example.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户上下文对象，保存当前登录用户信息
 * 在 TokenInterceptor 中设置，通过 UserContextHolder 获取
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserContext {
    private Long userId;
    private String username;
    private String token;
}
