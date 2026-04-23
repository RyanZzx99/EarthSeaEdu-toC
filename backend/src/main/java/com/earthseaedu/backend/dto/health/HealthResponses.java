package com.earthseaedu.backend.dto.health;

/**
 * 健康检查相关响应 DTO。
 */
public final class HealthResponses {

    private HealthResponses() {
    }

    /**
     * 根路径响应。
     *
     * @param message 服务启动提示文案
     */
    public record RootResponse(String message) {
    }

    /**
     * 基础健康检查响应。
     *
     * @param status 服务状态
     */
    public record HealthResponse(String status) {
    }

    /**
     * 数据库健康检查响应。
     *
     * @param status 整体状态
     * @param database 数据库连接状态说明
     */
    public record DatabaseHealthResponse(String status, String database) {
    }
}
