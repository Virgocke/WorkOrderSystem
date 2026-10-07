package com.WorkOrder.auth.service.impl;

import com.WorkOrder.auth.mapper.UsersMapper;
import com.WorkOrder.auth.service.MailService;
import com.WorkOrder.auth.service.SendCodeService;
import com.WorkOrder.auth.util.CodeUtil;
import com.WorkOrder.enums.SystemExceptionEnum;
import com.WorkOrder.exception.SystemException;
import com.WorkOrder.user.model.Users;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * @author Virgor
 * @date 2026年09月12日 00:33
 * @description 生成邮箱验证码并保存到 Redis，限制重复发送并在验证成功后消费验证码。
 */
@Service
@RequiredArgsConstructor
public class SendCodeServiceImpl implements SendCodeService {

    private final StringRedisTemplate redisTemplate;
    private final MailService mailService;
    private final UsersMapper usersMapper;

    private static final String CODE_PREFIX = "email:code:";
    private static final long CODE_EXPIRE_SECONDS = 300; // 5分钟
    private static final long SEND_INTERVAL_SECONDS = 60; // 60秒内只能发一次

    /**
     * 发送验证码
     *
     * @param email 邮箱地址
     */
    @Async
    @Override
    public void sendCode(String email) {
        Users user = usersMapper.selectByEmail(email);
        if (user == null){
            throw new SystemException(SystemExceptionEnum.USER_NOT_FOUND);
        }

        String key = CODE_PREFIX + email;

        // 防刷：如果已存在且剩余有效期大于 240 秒（即 60 秒内已发送过）
        Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (expire != null && expire > CODE_EXPIRE_SECONDS - SEND_INTERVAL_SECONDS) {
            throw new SystemException(SystemExceptionEnum.CODE_IS_EXPIRED);
        }

        String code = CodeUtil.generate6DigitCode();
        redisTemplate.opsForValue().set(key, code, CODE_EXPIRE_SECONDS, TimeUnit.SECONDS);

        // 异步发送邮件，避免阻塞
        mailService.sendVerificationCode(email, code);
    }

    /**
     * 验证验证码
     *
     * @param email 邮箱地址
     * @param code 业务代码
     * @return 验证结果
     */
    @Override
    public boolean verifyCode(String email, String code) {
        String key = CODE_PREFIX + email;
        String stored = redisTemplate.opsForValue().get(key);
        if (stored != null && stored.equals(code)) {
            redisTemplate.delete(key);
            return true;
        }
        return false;
    }
}
