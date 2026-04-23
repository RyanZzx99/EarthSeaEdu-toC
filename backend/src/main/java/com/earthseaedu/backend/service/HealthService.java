package com.earthseaedu.backend.service;

import com.earthseaedu.backend.dto.health.HealthResponses;

/**
 * 健康检查服务。
 */
public interface HealthService {

    /**
     * 返回根路径响应。
     *
     * @return 服务根路径信息
     */
    HealthResponses.RootResponse getRoot();

    /**
     * 返回应用健康状态。
     *
     * @return 基础健康状态
     */
    HealthResponses.HealthResponse getHealth();

    /**
     * 返回数据库健康状态。
     *
     * @return 数据库健康检查结果
     */
    HealthResponses.DatabaseHealthResponse getDatabaseHealth();
}
