package com.earthseaedu.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.earthseaedu.backend.controller.HealthController;
import com.earthseaedu.backend.controller.StudentProfileGuidedController;
import com.earthseaedu.backend.dto.guided.StudentProfileGuidedRequests;
import com.earthseaedu.backend.service.HealthService;
import com.earthseaedu.backend.service.StudentProfileGuidedService;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.http.HttpHeaders;
import org.springframework.web.method.HandlerMethod;

class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void openApiBeanRegistersBearerSecurityScheme() {
        EarthSeaProperties properties = new EarthSeaProperties();
        properties.setAppName("EarthSeaEdu API");

        OpenAPI openApi = config.earthSeaEduOpenApi(properties);

        assertThat(openApi.getInfo().getTitle()).isEqualTo("EarthSeaEdu API");
        assertThat(openApi.getComponents().getSecuritySchemes())
            .containsKey("bearerAuth");
        assertThat(openApi.getComponents().getSecuritySchemes().get("bearerAuth").getScheme())
            .isEqualTo("bearer");
    }

    @Test
    void authorizationHeaderIsConvertedToSecurityRequirement() throws Exception {
        OperationCustomizer customizer = config.bearerAuthOperationCustomizer();
        StudentProfileGuidedController controller = new StudentProfileGuidedController(mock(StudentProfileGuidedService.class));
        Method method = StudentProfileGuidedController.class.getMethod(
            "submitAnswer",
            String.class,
            StudentProfileGuidedRequests.GuidedAnswerPayload.class,
            String.class
        );
        HandlerMethod handlerMethod = new HandlerMethod(controller, method);
        Operation operation = new Operation().parameters(new ArrayList<>(List.of(
            new Parameter().name("sessionId").in("path").required(true),
            new Parameter().name(HttpHeaders.AUTHORIZATION).in("header")
        )));

        Operation customized = customizer.customize(operation, handlerMethod);

        assertThat(customized.getParameters())
            .extracting(Parameter::getName)
            .containsExactly("sessionId");
        assertThat(customized.getSecurity())
            .hasSize(1)
            .allSatisfy(requirement -> assertThat(requirement).containsKey("bearerAuth"));
    }

    @Test
    void operationsWithoutAuthorizationHeaderRemainUnsecured() throws Exception {
        OperationCustomizer customizer = config.bearerAuthOperationCustomizer();
        HealthController controller = new HealthController(mock(HealthService.class));
        Method method = HealthController.class.getMethod("health");
        HandlerMethod handlerMethod = new HandlerMethod(controller, method);
        Operation operation = new Operation().parameters(new ArrayList<>(List.of(
            new Parameter().name("status").in("query")
        )));

        Operation customized = customizer.customize(operation, handlerMethod);

        assertThat(customized.getParameters())
            .extracting(Parameter::getName)
            .containsExactly("status");
        assertThat(customized.getSecurity()).isNull();
    }
}
