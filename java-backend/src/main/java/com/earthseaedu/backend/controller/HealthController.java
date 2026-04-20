package com.earthseaedu.backend.controller;

import com.earthseaedu.backend.config.EarthSeaProperties;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final EarthSeaProperties properties;

    public HealthController(JdbcTemplate jdbcTemplate, EarthSeaProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @GetMapping("/")
    public Map<String, String> root() {
        return Map.of("message", properties.getAppName() + " is running");
    }

    @GetMapping("/api/v1/health")
    public HealthResponse health() {
        return new HealthResponse("ok");
    }

    @GetMapping("/api/v1/db-health")
    public DatabaseHealthResponse dbHealth() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return new DatabaseHealthResponse("ok", "connected");
        } catch (Exception exception) {
            return new DatabaseHealthResponse("error", exception.getClass().getSimpleName());
        }
    }

    public record HealthResponse(String status) {
    }

    public record DatabaseHealthResponse(String status, String database) {
    }
}
