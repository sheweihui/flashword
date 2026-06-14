package org.example.Interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.example.context.UserContextHolder;
import org.example.entity.User;
import org.example.entity.UserContext;
import org.example.utils.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

import static org.example.utils.UserEnum.USER_TOKEN;
@Component
@Slf4j
public class TokenInterceptor implements HandlerInterceptor {
    @Autowired
    private RedisUtil redisUtil;
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取请求头中的token
        String token =extractToken(request);
        // 判断token是否存在
        if (token == null || token.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        // 验证token
        String redisKey = USER_TOKEN.getValue() + ":" + token;
        Object user = redisUtil.get(redisKey);
        if (user == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        redisUtil.expire(redisKey, 2, TimeUnit.HOURS);
        // 可以将用户信息存入request,方便后续使用
        request.setAttribute("userInfo", user);
        User user1 = (User) user;
        UserContext userContext = new UserContext(((User) user).getId(), user1.getUsername(), token);
        UserContextHolder.set(userContext);
        log.debug("🔐 Token验证成功 | 用户: {} | ID: {}", user1.getNickname(), user1.getId());
        return true;
    }

    private String extractToken (HttpServletRequest  request) {
        // 优先尝试从 Authorization 头获取
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }

        // 其次尝试从 token 头获取
        String token = request.getHeader("token");
        if (token != null && !token.isEmpty()) {
            return token;
        }
        // 最后尝试从参数获取
        return request.getParameter("token");
    }
}
