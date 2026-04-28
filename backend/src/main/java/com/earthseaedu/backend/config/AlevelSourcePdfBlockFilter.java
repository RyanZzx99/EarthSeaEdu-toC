package com.earthseaedu.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AlevelSourcePdfBlockFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (isAlevelSourcePdf(request.getRequestURI())) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAlevelSourcePdf(String requestUri) {
        String decoded = requestUri == null
            ? ""
            : URLDecoder.decode(requestUri, StandardCharsets.UTF_8);
        String normalized = decoded.replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.startsWith("/exam-assets/alevel/source-files/")
            && normalized.endsWith(".pdf");
    }
}
