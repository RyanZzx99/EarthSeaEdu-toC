package com.earthseaedu.backend.service;

/**
 * 短信验证码缓存服务，负责验证码、发送频控和微信登录 state 的缓存校验。
 */
public interface SmsCodeCacheService {

    /**
     * 创建微信登录 state 缓存并设置过期时间。
     *
     * @param state 微信登录 state
     * @param expireMinutes 过期分钟数
     */
    void createWechatLoginState(String state, int expireMinutes);

    /**
     * 消费并校验微信登录 state，防止回调重放。
     *
     * @param state 微信登录 state
     * @return 校验结果。
     */
    boolean consumeWechatLoginState(String state);

    /**
     * 校验手机号在指定业务场景下是否允许继续发送验证码。
     *
     * @param mobile 手机号
     * @param bizType 业务场景
     */
    void validateSmsSendAllowed(String mobile, String bizType);

    /**
     * 保存短信验证码和业务场景到缓存。
     *
     * @param mobile 手机号
     * @param bizType 业务场景
     * @param code 验证码、邀请码或回调 code
     */
    void saveSmsCode(String mobile, String bizType, String code);

    /**
     * 校验短信验证码是否匹配并在成功后消费。
     *
     * @param mobile 手机号
     * @param bizType 业务场景
     * @param code 验证码、邀请码或回调 code
     * @return 校验结果。
     */
    boolean verifySmsCode(String mobile, String bizType, String code);
}
