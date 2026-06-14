package org.example.service;

import org.example.entity.User;

/**
 * 用户服务接口
 */
public interface UserService {
    
    /**
     * 用户注册
     */
    User register(String username, String password, String nickname);
    
    /**
     * 用户登录
     */
    User login(String username, String password);
    
    /**
     * 根据ID获取用户
     */
    User getById(Long id);

    void deductPoints(long id, long points, String orderNumber);
}
