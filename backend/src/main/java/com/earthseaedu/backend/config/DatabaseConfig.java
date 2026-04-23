package com.earthseaedu.backend.config;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Locale;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
public class DatabaseConfig {

    @Bean(destroyMethod = "close")
    public MysqlSshTunnelManager mysqlSshTunnelManager(EarthSeaProperties properties) {
        return new MysqlSshTunnelManager(properties);
    }

    @Bean(destroyMethod = "close")
    public DataSource dataSource(EarthSeaProperties properties, MysqlSshTunnelManager tunnelManager) {
        EarthSeaProperties.Mysql mysql = properties.getMysql();
        String host = mysql.getHost();
        int port = mysql.getPort();

        if (properties.getSsh().isEnabled()) {
            host = properties.getSsh().getLocalBindHost();
            port = tunnelManager.ensureStarted();
        }

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("earthsea-hikari");
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setJdbcUrl(buildJdbcUrl(mysql, host, port));
        dataSource.setUsername(requireText(mysql.getUser(), "MYSQL_USER"));
        dataSource.setPassword(mysql.getPassword());
        dataSource.setConnectionTestQuery("SELECT 1");
        return dataSource;
    }

    static String buildJdbcUrl(EarthSeaProperties.Mysql mysql, String host, int port) {
        return "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=%s&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true"
            .formatted(
                requireText(host, "MYSQL_HOST"),
                port,
                requireText(mysql.getDatabase(), "MYSQL_DATABASE"),
                toJdbcCharset(mysql.getCharset())
            );
    }

    static String toJdbcCharset(String charset) {
        if (!StringUtils.hasText(charset)) {
            return "UTF-8";
        }
        String normalized = charset.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "utf8", "utf8mb4", "utf-8" -> "UTF-8";
            default -> charset.trim();
        };
    }

    private static String requireText(String value, String envName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(envName + " must be configured");
        }
        return value.trim();
    }
}
