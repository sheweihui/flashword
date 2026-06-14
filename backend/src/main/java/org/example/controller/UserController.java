package org.example.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.Result;
import org.example.dto.UserDTO;
import org.example.entity.User;
import org.example.entity.UserPointsAccount;
import org.example.mq.producer.MessageProducer;
import org.example.mq.producer.UserMessageProducer;
import org.example.service.UserPointsAccountService;
import org.example.service.UserService;
import org.example.utils.RedisUtil;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.example.constant.RedisKeys;
import static org.example.utils.UserEnum.USER_TOKEN;

/**
 * 用户控制器
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final RedisUtil redisUtil;
    private final MessageProducer messageProducer;
    private final UserMessageProducer userMessageProducer;
    private final UserPointsAccountService userPointsAccountService;

    private static final long EXPIRE_TIME = 2;
    private static final TimeUnit TOKEN_TIME_UTIL = TimeUnit.HOURS;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<User> register(@RequestBody UserDTO userDTO) {
        String username = userDTO.getUsername();
        String password = userDTO.getPassword();
        String nickname = userDTO.getNickname();
        User user = userService.register(username, password, nickname);
        log.info("✅ [用户注册] 成功 | 用户名: {} | ID: {}", username, user.getId());
        messageProducer.CreateUserAccount(user.getId());
        return Result.success("注册成功", user);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result login(@RequestBody UserDTO userDTO) {
        log.info("🔐 [用户登录] 开始登录 | 用户名: {}", userDTO.getUsername());

        User user = userService.login(userDTO.getUsername(), userDTO.getPassword());

        // 清除密码信息
        user.setPassword(null);
        String token = UUID.randomUUID().toString().replace("-", "");
        String redisKey = USER_TOKEN.getValue() + ":" + token;
        redisUtil.set(redisKey, user, EXPIRE_TIME, TOKEN_TIME_UTIL);

        log.info("✅ [用户登录] 登录成功 | 用户ID: {} | 用户名: {} | Token: {}",
                user.getId(), userDTO.getUsername(), token);

        // 同步获取/创建积分账户，并写入 Redis 缓存
        UserPointsAccount pointsAccount = userPointsAccountService.getAccountByUserId(user.getId());
        if (pointsAccount == null) {
            pointsAccount = userPointsAccountService.createAccount(user.getId());
        }
        redisUtil.set(RedisKeys.userPoints(user.getId()), pointsAccount.getBalance(), EXPIRE_TIME, TOKEN_TIME_UTIL);

        // 发送异步消息，缓存用户其他数据到Redis（单词本、单词列表等）
        userMessageProducer.sendUserLoginMessage(user.getId(), token);

        // 返回 token + 用户信息 + 积分余额
        Map<String, Object> loginResult = new HashMap<>();
        loginResult.put("token", token);
        loginResult.put("userId", user.getId());
        loginResult.put("username", user.getUsername());
        loginResult.put("nickname", user.getNickname() != null ? user.getNickname() : user.getUsername());
        loginResult.put("points", pointsAccount.getBalance());
        return Result.success("登录成功", loginResult);
    }

    /**
     * 获取用户信息
     */
    @GetMapping("/{id}")
    public Result<User> getUserInfo(@PathVariable Long id) {
        User user = userService.getById(id);
        if (user == null) {
            return Result.error("用户不存在");
        }
        user.setPassword(null);
        return Result.success(user);
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public Result logout(@RequestBody Map<String, String> request) {
        String token = request.get("token");
        if (token == null || token.isEmpty()) {
            return Result.error("token不能为空");
        }
        log.info("🚪 [用户退出] 开始退出登录 | token: {}", token);
        redisUtil.delete(USER_TOKEN.getValue() + ":" + token);
        org.example.context.UserContextHolder.clear();
        log.info("✅ [用户退出] 成功 | token: {}", token);
        return Result.success("退出登录成功");
    }

    /**
     * 验证token是否有效
     */
    @PostMapping("/validate")
    public Result<Boolean> validateToken(@RequestBody Map<String, String> request) {
        return Result.success(redisUtil.hasKey(USER_TOKEN.getValue() + ":" + request.get("token")));
    }
}
