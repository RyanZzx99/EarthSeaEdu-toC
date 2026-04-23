package com.earthseaedu.backend.controller;

import com.earthseaedu.backend.dto.health.HealthResponses;
import com.earthseaedu.backend.service.HealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查接口。
 */
@RestController
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    /**
     * 根路径探活接口。
     *
     * @return 服务启动信息
     */
    @GetMapping("/")
    public HealthResponses.RootResponse root() {
        return healthService.getRoot();
    }

    /**
     * 应用健康检查接口。
     *
     * @return 基础健康状态
     */
    @GetMapping({"/health", "/api/v1/health"})
    public HealthResponses.HealthResponse health() {
        return healthService.getHealth();
    }

    /**
     * 数据库健康检查接口。
     *
     * @return 数据库连接状态
     */
    @GetMapping("/api/v1/db-health")
    public HealthResponses.DatabaseHealthResponse dbHealth() {
        return healthService.getDatabaseHealth();
    }
}
