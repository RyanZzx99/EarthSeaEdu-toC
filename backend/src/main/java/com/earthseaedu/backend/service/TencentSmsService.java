package com.earthseaedu.backend.service;

/**
 * 腾讯短信服务，负责发送业务验证码。
 */
public interface TencentSmsService {

    /**
     * 通过腾讯短信通道发送指定业务场景的验证码。
     *
     * @param mobile 手机号
     * @param bizType 业务场景
     * @return 生成或解析出的字符串结果。
     */
    String sendSmsCode(String mobile, String bizType);
}
