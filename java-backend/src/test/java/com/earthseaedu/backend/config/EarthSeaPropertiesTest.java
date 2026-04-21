package com.earthseaedu.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EarthSeaPropertiesTest {

    @Test
    void defaultsExposeMysqlAndSshSettings() {
        EarthSeaProperties properties = new EarthSeaProperties();

        assertThat(properties.getMysql().getHost()).isEqualTo("127.0.0.1");
        assertThat(properties.getMysql().getPort()).isEqualTo(3306);
        assertThat(properties.getMysql().getDatabase()).isEqualTo("earthseaedu");
        assertThat(properties.getMysql().getCharset()).isEqualTo("utf8mb4");

        assertThat(properties.getSsh().isEnabled()).isFalse();
        assertThat(properties.getSsh().getPort()).isEqualTo(22);
        assertThat(properties.getSsh().getRemoteBindHost()).isEqualTo("127.0.0.1");
        assertThat(properties.getSsh().getRemoteBindPort()).isEqualTo(3306);
        assertThat(properties.getSsh().getLocalBindHost()).isEqualTo("127.0.0.1");
        assertThat(properties.getSsh().getLocalBindPort()).isZero();

        assertThat(properties.getAiRuntime().getModelNonStreamRetryCount()).isEqualTo(1);
        assertThat(properties.getAiRuntime().getModelNonStreamRetryBackoffSeconds()).isEqualTo(1.0);
    }
}
