package com.earthseaedu.backend.service.impl;

import cn.hutool.core.net.url.UrlBuilder;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.earthseaedu.backend.config.EarthSeaProperties;
import com.earthseaedu.backend.exception.ApiException;
import com.earthseaedu.backend.service.WechatService;
import com.earthseaedu.backend.service.WechatService.WechatAccessInfo;
import com.earthseaedu.backend.service.WechatService.WechatUserInfo;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * WechatServiceImpl 服务实现，承载对应服务接口的业务规则和事务编排。
 */
@Service
public class WechatServiceImpl implements WechatService {

    private final EarthSeaProperties properties;

    /**
     * 创建 WechatServiceImpl 实例。
     */
    public WechatServiceImpl(EarthSeaProperties properties) {
        this.properties = properties;
    }

    /**
     * {@inheritDoc}
     */
    public String buildWechatAuthorizeUrl(String state) {
        validateWechatOauthConfig();
        return UrlBuilder.ofHttp("https://open.weixin.qq.com/connect/qrconnect")
            .addQuery("appid", properties.getWechat().getOpenAppId())
            .addQuery("redirect_uri", properties.getWechat().getOpenRedirectUri())
            .addQuery("response_type", "code")
            .addQuery("scope", "snsapi_login")
            .addQuery("state", state)
            .build() + "#wechat_redirect";
    }

    /**
     * {@inheritDoc}
     */
    public WechatAccessInfo getWechatAccessInfoByCode(String code) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("appid", properties.getWechat().getOpenAppId());
        params.put("secret", properties.getWechat().getOpenAppSecret());
        params.put("code", code);
        params.put("grant_type", "authorization_code");

        HttpResponse response = HttpRequest.get("https://api.weixin.qq.com/sns/oauth2/access_token")
            .form(params)
            .timeout(10_000)
            .execute();
        Map<String, Object> body = JSONUtil.parseObj(response.body());

        if (body.containsKey("errcode")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "微信换取 access_token 失败: " + response.body());
        }

        String openid = stringValue(body.get("openid"));
        if (CharSequenceUtil.isBlank(openid)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "微信返回数据缺少 openid");
        }
        return new WechatAccessInfo(
            stringValue(body.get("access_token")),
            openid,
            stringValue(body.get("unionid"))
        );
    }

    /**
     * {@inheritDoc}
     */
    public WechatUserInfo getWechatUserInfo(String accessToken, String openid) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("access_token", accessToken);
            params.put("openid", openid);
            HttpResponse response = HttpRequest.get("https://api.weixin.qq.com/sns/userinfo")
                .form(params)
                .timeout(10_000)
                .execute();
            Map<String, Object> body = JSONUtil.parseObj(response.body());
            if (body.containsKey("errcode")) {
                return WechatUserInfo.empty();
            }
            return new WechatUserInfo(
                stringValue(body.get("nickname")),
                stringValue(body.get("headimgurl")),
                body.get("sex") == null ? null : Integer.parseInt(String.valueOf(body.get("sex"))),
                stringValue(body.get("province")),
                stringValue(body.get("city")),
                stringValue(body.get("country"))
            );
        } catch (Exception exception) {
            return WechatUserInfo.empty();
        }
    }

    /**
     * {@inheritDoc}
     */
    public String resolveFrontendLoginPageUrl() {
        String configuredLoginUrl = CharSequenceUtil.nullToEmpty(properties.getFrontendLoginPageUrl()).trim();
        String derivedLoginUrl = buildLoginPageUrlFromRedirectUri();

        if (CharSequenceUtil.isNotBlank(configuredLoginUrl)
            && !configuredLoginUrl.contains("localhost")
            && !configuredLoginUrl.contains("127.0.0.1")) {
            return CharSequenceUtil.removeSuffix(configuredLoginUrl, "/");
        }
        if (CharSequenceUtil.isNotBlank(derivedLoginUrl)) {
            return CharSequenceUtil.removeSuffix(derivedLoginUrl, "/");
        }
        if (CharSequenceUtil.isNotBlank(configuredLoginUrl)) {
            return CharSequenceUtil.removeSuffix(configuredLoginUrl, "/");
        }
        return "http://localhost:5173/login";
    }

    private void validateWechatOauthConfig() {
        String appId = CharSequenceUtil.nullToEmpty(properties.getWechat().getOpenAppId()).trim();
        String redirectUri = CharSequenceUtil.nullToEmpty(properties.getWechat().getOpenRedirectUri()).trim();
        if (CharSequenceUtil.isBlank(appId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "微信登录未配置 WECHAT_OPEN_APP_ID");
        }
        if (CharSequenceUtil.isBlank(redirectUri)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "微信登录未配置 WECHAT_OPEN_REDIRECT_URI");
        }

        try {
            URI uri = new URI(redirectUri);
            String host = uri.getHost();
            if (CharSequenceUtil.isBlank(uri.getScheme()) || CharSequenceUtil.isBlank(host)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "WECHAT_OPEN_REDIRECT_URI 必须是完整的 http(s) URL");
            }
            String lowerHost = host.toLowerCase();
            if ("localhost".equals(lowerHost) || "127.0.0.1".equals(lowerHost)) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "微信登录回调地址不能使用 localhost，请改成微信开放平台已配置的公网域名"
                );
            }
            if (InetAddressValidator.isIpAddress(lowerHost)) {
                throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "微信登录回调地址不能使用 IP，请改成微信开放平台已配置的公网域名"
                );
            }
        } catch (URISyntaxException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WECHAT_OPEN_REDIRECT_URI 必须是完整的 http(s) URL");
        }
    }

    private String buildLoginPageUrlFromRedirectUri() {
        String redirectUri = CharSequenceUtil.nullToEmpty(properties.getWechat().getOpenRedirectUri()).trim();
        if (CharSequenceUtil.isBlank(redirectUri)) {
            return null;
        }

        try {
            URI uri = new URI(redirectUri);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            int port = uri.getPort();
            return port > 0
                ? uri.getScheme() + "://" + uri.getHost() + ":" + port + "/login"
                : uri.getScheme() + "://" + uri.getHost() + "/login";
        } catch (URISyntaxException exception) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static final class InetAddressValidator {

        private InetAddressValidator() {
        }

        static boolean isIpAddress(String host) {
            try {
                InetAddress.getByName(host);
                return host.chars().allMatch(ch -> Character.isDigit(ch) || ch == '.' || ch == ':');
            } catch (Exception exception) {
                return false;
            }
        }
    }
}
