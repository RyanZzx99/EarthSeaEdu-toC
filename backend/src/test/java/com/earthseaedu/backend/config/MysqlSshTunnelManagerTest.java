package com.earthseaedu.backend.config;

import com.jcraft.jsch.JSchException;
import java.net.BindException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MysqlSshTunnelManagerTest {

    @Test
    void shouldRetryWithRandomLocalPortWhenConfiguredPortIsOccupied() {
        JSchException ex = new JSchException(
            "PortForwardingL: local port 127.0.0.1:3307 cannot be bound.",
            new BindException("Address already in use: bind")
        );

        assertTrue(MysqlSshTunnelManager.shouldRetryWithRandomLocalPort(3307, ex));
    }

    @Test
    void shouldNotRetryWithRandomLocalPortWhenConfiguredPortIsDynamic() {
        JSchException ex = new JSchException(
            "PortForwardingL: local port 127.0.0.1:0 cannot be bound.",
            new BindException("Address already in use: bind")
        );

        assertFalse(MysqlSshTunnelManager.shouldRetryWithRandomLocalPort(0, ex));
    }

    @Test
    void shouldNotRetryWithRandomLocalPortForOtherJschErrors() {
        JSchException ex = new JSchException("Auth fail");

        assertFalse(MysqlSshTunnelManager.shouldRetryWithRandomLocalPort(3307, ex));
    }
}
