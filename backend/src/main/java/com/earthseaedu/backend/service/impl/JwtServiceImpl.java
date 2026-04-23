package com.earthseaedu.backend.service.impl;

import com.earthseaedu.backend.config.EarthSeaProperties;
import com.earthseaedu.backend.exception.ApiException;
import com.earthseaedu.backend.service.JwtService;
import com.earthseaedu.backend.support.RequestHeaderSupport;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * JwtServiceImpl 服务实现，承载对应服务接口的业务规则和事务编排。
 */
@Service
public class JwtServiceImpl implements JwtService {

    private final EarthSeaProperties properties;
    private final SecretKey secretKey;

    /**
     * 创建 JwtServiceImpl 实例。
     */
    public JwtServiceImpl(EarthSeaProperties properties) {
        this.properties = properties;
        this.secretKey = new SecretKeySpec(
            properties.getJwt().getSecretKey().getBytes(StandardCharsets.UTF_8),
            "HmacSHA256"
        );
    }

    /**
     * {@inheritDoc}
     */
    public String createAccessToken(String userId) {
        return createToken(
            Map.of("sub", userId, "token_use", "access"),
            properties.getJwt().getAccessTokenExpireMinutes()
        );
    }

    /**
     * {@inheritDoc}
     */
    public String createBindToken(String userId) {
        return createToken(
            Map.of("sub", userId, "token_use", "bind_mobile"),
            properties.getJwt().getBindTokenExpireMinutes()
        );
    }

    /**
     * {@inheritDoc}
     */
    public String createWechatRegisterToken(String userId, String openid) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", userId);
        payload.put("openid", openid);
        payload.put("token_use", "wechat_register");
        return createToken(payload, properties.getJwt().getBindTokenExpireMinutes());
    }

    /**
     * {@inheritDoc}
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (JwtException | IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "token 无效或已过期");
        }
    }

    /**
     * {@inheritDoc}
     */
    public Claims parseTokenNullable(String token) {
        try {
            return parseToken(token);
        } catch (ApiException exception) {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    public String requireCurrentUserId(String authorizationHeader) {
        String token = RequestHeaderSupport.extractBearerToken(authorizationHeader);
        if (token == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "未登录或 token 缺失");
        }

        Claims claims = parseToken(token);
        if (!"access".equals(claims.get("token_use", String.class))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "token 类型错误");
        }
        String userId = claims.getSubject();
        if (userId == null || userId.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "token 缺少用户信息");
        }
        return userId;
    }

    /**
     * {@inheritDoc}
     */
    public Claims requireTokenUse(String token, String tokenUse, String emptyMessage, String invalidTypeMessage) {
        Claims claims = parseTokenNullable(token);
        if (claims == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, emptyMessage);
        }
        if (!tokenUse.equals(claims.get("token_use", String.class))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, invalidTypeMessage);
        }
        if (claims.getSubject() == null || claims.getSubject().isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "token 缺少用户信息");
        }
        return claims;
    }

    private String createToken(Map<String, Object> payload, long expireMinutes) {
        if (!"HS256".equalsIgnoreCase(properties.getJwt().getAlgorithm())) {
            throw new IllegalStateException("当前 Java 版本仅支持 HS256");
        }

        Instant expireAt = Instant.now().plus(expireMinutes, ChronoUnit.MINUTES);
        return Jwts.builder()
            .claims(new LinkedHashMap<>(payload))
            .expiration(Date.from(expireAt))
            .signWith(secretKey, SignatureAlgorithm.HS256)
            .compact();
    }
}
