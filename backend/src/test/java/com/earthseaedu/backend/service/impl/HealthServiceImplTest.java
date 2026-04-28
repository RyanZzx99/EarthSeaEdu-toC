package com.earthseaedu.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.earthseaedu.backend.config.EarthSeaProperties;
import com.earthseaedu.backend.dto.health.HealthResponses;
import com.earthseaedu.backend.mapper.HealthMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HealthServiceImplTest {

    private HealthMapper healthMapper;
    private EarthSeaProperties properties;
    private HealthServiceImpl healthService;

    @BeforeEach
    void setUp() {
        healthMapper = mock(HealthMapper.class);
        properties = mock(EarthSeaProperties.class);
        healthService = new HealthServiceImpl(healthMapper, properties);
    }

    @Test
    void getRootReturnsConfiguredAppName() {
        given(properties.getAppName()).willReturn("EarthSeaEdu API");

        HealthResponses.RootResponse response = healthService.getRoot();

        assertThat(response.message()).isEqualTo("EarthSeaEdu API is running");
    }

    @Test
    void getDatabaseHealthReturnsOkWhenMapperResponds() {
        given(healthMapper.selectOne()).willReturn(1);

        HealthResponses.DatabaseHealthResponse response = healthService.getDatabaseHealth();

        assertThat(response.status()).isEqualTo("ok");
        assertThat(response.database()).isEqualTo("connected");
    }

    @Test
    void getDatabaseHealthReturnsErrorWhenMapperThrows() {
        given(healthMapper.selectOne()).willThrow(new IllegalStateException("boom"));

        HealthResponses.DatabaseHealthResponse response = healthService.getDatabaseHealth();

        assertThat(response.status()).isEqualTo("error");
        assertThat(response.database()).isEqualTo("IllegalStateException");
    }
}
