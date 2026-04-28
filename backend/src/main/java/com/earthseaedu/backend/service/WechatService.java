package com.earthseaedu.backend.service;

/**
 * 微信开放平台服务，负责授权地址、访问令牌和用户资料获取。
 */
public interface WechatService {

    /**
     * 构建微信网页授权跳转地址。
     *
     * @param state 微信登录 state
     * @return 生成或解析出的字符串结果。
     */
    String buildWechatAuthorizeUrl(String state);

    /**
     * 使用微信回调 code 换取访问令牌和 openid 信息。
     *
     * @param code 验证码、邀请码或回调 code
     * @return 处理后的响应对象。
     */
    WechatAccessInfo getWechatAccessInfoByCode(String code);

    /**
     * 使用微信访问令牌读取用户资料。
     *
     * @param accessToken 访问令牌
     * @param openid 微信 openid
     * @return 处理后的响应对象。
     */
    WechatUserInfo getWechatUserInfo(String accessToken, String openid);

    /**
     * 解析微信登录流程回跳的前端登录页地址。
     *
     * @return 生成或解析出的字符串结果。
     */
    String resolveFrontendLoginPageUrl();

    /**
     * 微信授权访问结果，封装 access token、openid 和 unionid。
     *
     * @param accessToken 访问令牌
     * @param openid 微信 openid
     * @param unionid 微信 unionid
     */
    record WechatAccessInfo(String accessToken, String openid, String unionid) {
    }

    /**
     * 微信用户资料，封装昵称、头像和地区信息。
     *
     * @param nickname 昵称或微信昵称
     * @param headimgurl 微信头像地址
     * @param sex 性别
     * @param province 省份
     * @param city 城市
     * @param country 国家
     */
    record WechatUserInfo(
        String nickname,
        String headimgurl,
        Integer sex,
        String province,
        String city,
        String country
    ) {
        /**
         * 创建字段全空的微信用户资料对象。
         *
         * @return 处理后的响应对象。
         */
        public static WechatUserInfo empty() {
            return new WechatUserInfo(null, null, null, null, null, null);
        }
    }
}
