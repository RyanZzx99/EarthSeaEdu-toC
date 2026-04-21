package com.earthseaedu.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.earthseaedu.backend.dto.health.HealthResponses;
import com.earthseaedu.backend.exception.GlobalExceptionHandler;
import com.earthseaedu.backend.service.HealthService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class HealthControllerWebTest {

    private MockMvc mockMvc;
    private HealthService healthService;
    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        healthService = mock(HealthService.class);
        exceptionHandler = new GlobalExceptionHandler();
        mockMvc = MockMvcBuilders
            .standaloneSetup(new HealthController(healthService))
            .setControllerAdvice(exceptionHandler)
            .build();
    }

    @Test
    void healthAliasReturnsOk() throws Exception {
        given(healthService.getHealth()).willReturn(new HealthResponses.HealthResponse("ok"));

        mockMvc.perform(get("/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void apiHealthReturnsOk() throws Exception {
        given(healthService.getHealth()).willReturn(new HealthResponses.HealthResponse("ok"));

        mockMvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void rootReturnsAppMessage() throws Exception {
        given(healthService.getRoot()).willReturn(new HealthResponses.RootResponse("EarthSeaEdu API is running"));

        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("EarthSeaEdu API is running"));
    }

    @Test
    void noResourceFoundExceptionReturnsNotFound() {
        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleNoResourceFoundException(
            new NoResourceFoundException(HttpMethod.GET, "/missing-health")
        );

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).containsEntry("detail", "资源不存在");
    }
}
