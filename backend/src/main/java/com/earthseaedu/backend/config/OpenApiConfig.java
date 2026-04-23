package com.earthseaedu.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.method.HandlerMethod;

/**
 * OpenAPI 文档配置，负责暴露 Swagger UI 和基础文档元信息。
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI earthSeaEduOpenApi(EarthSeaProperties properties) {
        return new OpenAPI()
            .info(new Info()
                .title(properties.getAppName())
                .description("EarthSeaEdu Java 后端接口文档")
                .version("v0.1.0")
                .contact(new Contact().name("EarthSeaEdu Backend")))
            .components(new Components()
                .addSecuritySchemes(BEARER_AUTH_SCHEME, new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")));
    }

    @Bean
    public OperationCustomizer bearerAuthOperationCustomizer() {
        return (operation, handlerMethod) -> {
            if (!hasAuthorizationHeader(handlerMethod)) {
                return operation;
            }

            if (operation.getParameters() != null) {
                List<Parameter> filteredParameters = operation.getParameters().stream()
                    .filter(parameter ->
                        !"header".equalsIgnoreCase(parameter.getIn())
                            || !HttpHeaders.AUTHORIZATION.equalsIgnoreCase(parameter.getName())
                    )
                    .toList();
                if (filteredParameters.isEmpty()) {
                    operation.setParameters(null);
                } else {
                    operation.setParameters(filteredParameters);
                }
            }

            boolean hasSecurityRequirement = operation.getSecurity() != null
                && operation.getSecurity().stream().anyMatch(requirement -> requirement.containsKey(BEARER_AUTH_SCHEME));
            if (!hasSecurityRequirement) {
                operation.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH_SCHEME));
            }
            return operation;
        };
    }

    private boolean hasAuthorizationHeader(HandlerMethod handlerMethod) {
        for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
            RequestHeader requestHeader = parameter.getParameterAnnotation(RequestHeader.class);
            if (requestHeader == null) {
                continue;
            }

            if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(resolveHeaderName(requestHeader))) {
                return true;
            }
        }
        return false;
    }

    private String resolveHeaderName(RequestHeader requestHeader) {
        String headerName = requestHeader.name();
        if (!StringUtils.hasText(headerName)) {
            headerName = requestHeader.value();
        }
        return headerName == null ? "" : headerName.trim();
    }
}
