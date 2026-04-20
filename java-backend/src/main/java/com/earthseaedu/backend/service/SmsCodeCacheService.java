package com.earthseaedu.backend.service;

import cn.hutool.crypto.SecureUtil;
import com.earthseaedu.backend.config.EarthSeaProperties;
import com.earthseaedu.backend.exception.ApiException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SmsCodeCacheService {

    private final StringRedisTemplate stringRedisTemplate;
    private final EarthSeaProperties properties;
    private final DefaultRedisScript<Long> compareAndDeleteScript;

    public SmsCodeCacheService(StringRedisTemplate stringRedisTemplate, EarthSeaProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
        this.compareAndDeleteScript = new DefaultRedisScript<>();
        this.compareAndDeleteScript.setResultType(Long.class);
        this.compareAndDeleteScript.setScriptText(
            "local current = redis.call('GET', KEYS[1]);" +
                "if not current then return 0 end;" +
                "if current == ARGV[1] then redis.call('DEL', KEYS[1]); return 1 end;" +
                "return 0;"
        );
    }

    public void createWechatLoginState(String state, int expireMinutes) {
        stringRedisTemplate.opsForValue().set(wechatStateKey(state), "1", Duration.ofMinutes(expireMinutes));
    }

    public boolean consumeWechatLoginState(String state) {
        String result = stringRedisTemplate.opsForValue().getAndDelete(wechatStateKey(state));
        return result != null;
    }

    public void validateSmsSendAllowed(String mobile, String bizType) {
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(smsCooldownKey(mobile, bizType)))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "发送过于频繁，请稍后再试");
        }

        String dailyCountValue = stringRedisTemplate.opsForValue().get(smsDailyLimitKey(mobile, bizType));
        long dailyCount = dailyCountValue == null ? 0L : Long.parseLong(dailyCountValue);
        if (dailyCount >= properties.getSmsDailyLimit()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "今日验证码发送次数已达上限");
        }
    }

    public void saveSmsCode(String mobile, String bizType, String code) {
        stringRedisTemplate.opsForValue().set(
            smsCodeKey(mobile, bizType),
            sha256(code),
            Duration.ofMinutes(Math.max(1, properties.getSmsCodeExpireMinutes()))
        );
        stringRedisTemplate.opsForValue().set(
            smsCooldownKey(mobile, bizType),
            "1",
            Duration.ofSeconds(Math.max(1, properties.getSmsSendCooldownSeconds()))
        );

        Long count = stringRedisTemplate.opsForValue().increment(smsDailyLimitKey(mobile, bizType));
        if (Objects.equals(count, 1L)) {
            stringRedisTemplate.expire(
                smsDailyLimitKey(mobile, bizType),
                Duration.ofSeconds(secondsUntilNextUtcDay())
            );
        }
    }

    public boolean verifySmsCode(String mobile, String bizType, String code) {
        Long result = stringRedisTemplate.execute(
            compareAndDeleteScript,
            List.of(smsCodeKey(mobile, bizType)),
            sha256(code)
        );
        return Objects.equals(result, 1L);
    }

    private String smsCodeKey(String mobile, String bizType) {
        return "auth:sms:code:" + bizType + ":" + mobile;
    }

    private String smsCooldownKey(String mobile, String bizType) {
        return "auth:sms:cooldown:" + bizType + ":" + mobile;
    }

    private String smsDailyLimitKey(String mobile, String bizType) {
        return "auth:sms:daily:" + LocalDate.now(ZoneOffset.UTC) + ":" + bizType + ":" + mobile;
    }

    private String wechatStateKey(String state) {
        return "auth:wechat:state:" + state;
    }

    private String sha256(String value) {
        return SecureUtil.sha256(value);
    }

    private long secondsUntilNextUtcDay() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime nextDay = now.toLocalDate().plusDays(1).atStartOfDay();
        return Math.max(1L, Duration.between(now, nextDay).getSeconds());
    }
}
