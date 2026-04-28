package com.earthseaedu.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

class DatabaseConfigTest {

    private final DatabaseConfig config = new DatabaseConfig();

    @Test
    void dataSourceUsesDirectMysqlEndpointWhenSshDisabled() {
        EarthSeaProperties properties = new EarthSeaProperties();
        properties.getMysql().setHost("10.0.0.8");
        properties.getMysql().setPort(3307);
        properties.getMysql().setDatabase("earthsea_prod");
        properties.getMysql().setUser("db_user");
        properties.getMysql().setPassword("db_password");
        properties.getMysql().setCharset("utf8mb4");

        try (HikariDataSource dataSource = (HikariDataSource) config.dataSource(properties, new FakeTunnelManager(properties, 4306))) {
            assertThat(dataSource.getJdbcUrl())
                .isEqualTo(
                    "jdbc:mysql://10.0.0.8:3307/earthsea_prod?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true"
                );
            assertThat(dataSource.getUsername()).isEqualTo("db_user");
        }
    }

    @Test
    void dataSourceUsesLocalTunnelEndpointWhenSshEnabled() {
        EarthSeaProperties properties = new EarthSeaProperties();
        properties.getMysql().setDatabase("earthsea_prod");
        properties.getMysql().setUser("db_user");
        properties.getMysql().setPassword("db_password");
        properties.getSsh().setEnabled(true);
        properties.getSsh().setLocalBindHost("127.0.0.1");

        try (HikariDataSource dataSource = (HikariDataSource) config.dataSource(properties, new FakeTunnelManager(properties, 4306))) {
            assertThat(dataSource.getJdbcUrl())
                .isEqualTo(
                    "jdbc:mysql://127.0.0.1:4306/earthsea_prod?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true"
                );
        }
    }

    @Test
    void tunnelManagerValidatesRequiredSshFields() {
        EarthSeaProperties properties = new EarthSeaProperties();
        properties.getSsh().setEnabled(true);

        assertThatThrownBy(() -> new MysqlSshTunnelManager(properties).ensureStarted())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SSH_HOST");
    }

    private static final class FakeTunnelManager extends MysqlSshTunnelManager {

        private final int forwardedPort;

        private FakeTunnelManager(EarthSeaProperties properties, int forwardedPort) {
            super(properties);
            this.forwardedPort = forwardedPort;
        }

        @Override
        public synchronized int ensureStarted() {
            return forwardedPort;
        }

        @Override
        public synchronized void close() {
        }
    }
}
