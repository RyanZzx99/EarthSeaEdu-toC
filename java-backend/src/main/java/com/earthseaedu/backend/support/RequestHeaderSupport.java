package com.earthseaedu.backend.support;

import cn.hutool.core.text.CharSequenceUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

public final class RequestHeaderSupport {

    private RequestHeaderSupport() {
    }

    public static String extractBearerToken(String authorizationHeader) {
        if (CharSequenceUtil.isBlank(authorizationHeader)) {
            return null;
        }
        if (!authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        return CharSequenceUtil.trim(authorizationHeader.substring("Bearer ".length()));
    }

    public static String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        List<String> headerCandidates = List.of(
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP"
        );
        for (String header : headerCandidates) {
            String value = request.getHeader(header);
            if (CharSequenceUtil.isBlank(value) || "unknown".equalsIgnoreCase(value)) {
                continue;
            }
            int commaIndex = value.indexOf(',');
            return commaIndex >= 0 ? value.substring(0, commaIndex).trim() : value.trim();
        }
        return request.getRemoteAddr();
    }
}
