package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.UserPointsAccount;
import org.example.entity.UserPointsTransaction;
import org.example.mapper.UserPointsAccountMapper;
import org.example.mapper.UserPointsTransactionMapper;
import org.example.service.UserPointsAccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 用户积分账户服务实现类
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class UserPointsAccountServiceImpl implements UserPointsAccountService {

    private final UserPointsAccountMapper userPointsAccountMapper;
    private final UserPointsTransactionMapper userPointsTransactionMapper;

    @Override
    @Transactional
    public UserPointsAccount createAccount(Long userId) {
        long start = System.currentTimeMillis();
        long stepStart;

        // 幂等性检查：先查询是否已存在
        stepStart = System.currentTimeMillis();
        UserPointsAccount existing = userPointsAccountMapper.selectByUserId(userId);
        if (existing != null) {
            log.warn("⚠️ [积分账户] 用户 {} 的账户已存在，跳过创建", userId);
            return existing;
        }
        log.info("⏱️ [性能监控-createAccount] 步骤1-查询是否存在: {}ms", System.currentTimeMillis() - stepStart);

        stepStart = System.currentTimeMillis();
        UserPointsAccount account = new UserPointsAccount();
        account.setUserId(userId);
        account.setBalance(500); // 注册赠送500积分
        account.setTotalEarned(500);
        account.setTotalSpent(0);

        userPointsAccountMapper.insert(account);
        log.info("⏱️ [性能监控-createAccount] 步骤2-插入账户: {}ms", System.currentTimeMillis() - stepStart);

        // 记录交易
        stepStart = System.currentTimeMillis();
        UserPointsTransaction transaction = new UserPointsTransaction();
        transaction.setUserId(userId);
        transaction.setType(1); // 注册赠送
        transaction.setAmount(500);
        transaction.setBalanceAfter(500);
        transaction.setDescription("注册赠送积分");
        transaction.setIdempotencyKey("REG_" + userId + "_" + System.currentTimeMillis());

        userPointsTransactionMapper.insert(transaction);
        log.info("⏱️ [性能监控-createAccount] 步骤3-插入交易记录: {}ms", System.currentTimeMillis() - stepStart);

        log.info("✅ [积分账户] 创建成功 | 用户ID: {} | 初始积分: 500 | 总耗时: {}ms", userId, System.currentTimeMillis() - start);
        return account;
    }

    @Override
    public UserPointsAccount getAccountByUserId(Long userId) {
        return userPointsAccountMapper.selectByUserId(userId);
    }

    @Override
    @Transactional
    public Integer addPoints(Long userId, Integer amount, Integer type, String description, Long referenceId) {
        long start = System.currentTimeMillis();
        UserPointsAccount account = userPointsAccountMapper.selectByUserId(userId);
        if (account == null) {
            log.warn("⚠️ [积分账户] 用户 {} 的账户不存在，自动创建", userId);
            account = createAccount(userId);
        }

        int newBalance = account.getBalance() + amount;
        account.setBalance(newBalance);
        account.setTotalEarned(account.getTotalEarned() + amount);

        userPointsAccountMapper.updateBalance(userId, newBalance);

        // 记录交易
        UserPointsTransaction transaction = new UserPointsTransaction();
        transaction.setUserId(userId);
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setBalanceAfter(newBalance);
        transaction.setDescription(description);
        transaction.setReferenceId(referenceId);

        userPointsTransactionMapper.insert(transaction);

        log.info("✅ [增加积分] 用户ID: {} | 增加: {} | 余额: {} | 原因: {}", userId, amount, newBalance, description);
        log.info("耗时:{}", System.currentTimeMillis() - start);
        return newBalance;
    }

    @Override
    @Transactional
    public Integer deductPoints(Long userId, Integer amount, Integer type, String description, Long referenceId) {
        return deductPoints(userId, amount, type, description, referenceId, null);
    }

    /**
     * 扣除积分（支持幂等性标识）
     */
    @Override
    @Transactional
    public Integer deductPoints(Long userId, Integer amount, Integer type, String description, Long referenceId, String idempotencyKey) {
        UserPointsAccount account = userPointsAccountMapper.selectByUserId(userId);
        if (account == null) {
            throw new RuntimeException("积分账户不存在");
        }

        if (account.getBalance() < amount) {
            throw new RuntimeException("积分不足");
        }

        int newBalance = account.getBalance() - amount;
        account.setBalance(newBalance);
        account.setTotalSpent(account.getTotalSpent() + amount);

        userPointsAccountMapper.updateBalance(userId, newBalance);

        // 记录交易
        UserPointsTransaction transaction = new UserPointsTransaction();
        transaction.setUserId(userId);
        transaction.setType(type);
        transaction.setAmount(-amount);
        transaction.setBalanceAfter(newBalance);
        transaction.setDescription(description);
        transaction.setReferenceId(referenceId);
        transaction.setIdempotencyKey(idempotencyKey);

        userPointsTransactionMapper.insert(transaction);

        log.info("✅ [扣除积分] 用户ID: {} | 扣除: {} | 余额: {} | 原因: {}", userId, amount, newBalance, description);
        return newBalance;
    }
}
