package com.earthseaedu.backend.service.impl;

import com.earthseaedu.backend.config.EarthSeaProperties;
import com.earthseaedu.backend.dto.health.HealthResponses;
import com.earthseaedu.backend.mapper.HealthMapper;
import com.earthseaedu.backend.service.HealthService;
import org.springframework.stereotype.Service;

/**
 * 健康检查服务实现。
 */
@Service
public class HealthServiceImpl implements HealthService {

    private final HealthMapper healthMapper;
    private final EarthSeaProperties properties;

    /**
     * 创建 HealthServiceImpl 实例。
     */
    public HealthServiceImpl(HealthMapper healthMapper, EarthSeaProperties properties) {
        this.healthMapper = healthMapper;
        this.properties = properties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HealthResponses.RootResponse getRoot() {
        return new HealthResponses.RootResponse(properties.getAppName() + " is running");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HealthResponses.HealthResponse getHealth() {
        return new HealthResponses.HealthResponse("ok");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HealthResponses.DatabaseHealthResponse getDatabaseHealth() {
        try {
            Integer marker = healthMapper.selectOne();
            String database = Integer.valueOf(1).equals(marker) ? "connected" : "unexpected_result";
            String status = Integer.valueOf(1).equals(marker) ? "ok" : "error";
            return new HealthResponses.DatabaseHealthResponse(status, database);
        } catch (Exception exception) {
            return new HealthResponses.DatabaseHealthResponse("error", exception.getClass().getSimpleName());
        }
    }
}
