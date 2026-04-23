package com.earthseaedu.backend.service;

import io.jsonwebtoken.Claims;

/**
 * JWT 服务，负责令牌签发、解析和用途校验。
 */
public interface JwtService {

    /**
     * 为用户签发标准访问令牌。
     *
     * @param userId 用户 ID
     * @return 生成或解析出的字符串结果。
     */
    String createAccessToken(String userId);

    /**
     * 为用户签发微信绑定流程令牌。
     *
     * @param userId 用户 ID
     * @return 生成或解析出的字符串结果。
     */
    String createBindToken(String userId);

    /**
     * 为微信临时注册流程签发令牌。
     *
     * @param userId 用户 ID
     * @param openid 微信 openid
     * @return 生成或解析出的字符串结果。
     */
    String createWechatRegisterToken(String userId, String openid);

    /**
     * 解析 JWT 并在无效时抛出鉴权异常。
     *
     * @param token JWT 字符串
     * @return 处理后的响应对象。
     */
    Claims parseToken(String token);

    /**
     * 尝试解析 JWT，无效或为空时返回空结果。
     *
     * @param token JWT 字符串
     * @return 处理后的响应对象。
     */
    Claims parseTokenNullable(String token);

    /**
     * 从鉴权头中解析并返回当前用户 ID。
     *
     * @param authorizationHeader 当前请求鉴权头
     * @return 生成或解析出的字符串结果。
     */
    String requireCurrentUserId(String authorizationHeader);

    /**
     * 解析令牌并校验令牌用途。
     *
     * @param token JWT 字符串
     * @param tokenUse 期望的令牌用途
     * @param emptyMessage 令牌为空时的错误提示
     * @param invalidTypeMessage 令牌用途不匹配时的错误提示
     * @return 处理后的响应对象。
     */
    Claims requireTokenUse(String token, String tokenUse, String emptyMessage, String invalidTypeMessage);
}
