package com.earthseaedu.backend.config;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.net.BindException;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public class MysqlSshTunnelManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MysqlSshTunnelManager.class);

    private final EarthSeaProperties.Ssh ssh;
    private Session session;
    private Integer localForwardPort;

    public MysqlSshTunnelManager(EarthSeaProperties properties) {
        this.ssh = properties.getSsh();
    }

    public synchronized int ensureStarted() {
        if (!ssh.isEnabled()) {
            throw new IllegalStateException("SSH tunnel is disabled");
        }
        if (session != null && session.isConnected() && localForwardPort != null) {
            return localForwardPort;
        }

        requireText(ssh.getHost(), "SSH_HOST");
        requireText(ssh.getUser(), "SSH_USER");
        requireText(ssh.getRemoteBindHost(), "SSH_REMOTE_BIND_HOST");
        requireText(ssh.getLocalBindHost(), "SSH_LOCAL_BIND_HOST");

        Session candidate = null;
        try {
            candidate = new JSch().getSession(ssh.getUser(), ssh.getHost(), ssh.getPort());
            if (StringUtils.hasText(ssh.getPassword())) {
                candidate.setPassword(ssh.getPassword());
            }
            candidate.setConfig("StrictHostKeyChecking", "no");
            // Prevent the SSH tunnel from being dropped before the first lazy DB connection is created.
            candidate.setServerAliveInterval(30_000);
            candidate.setServerAliveCountMax(3);
            candidate.connect(10_000);
            int forwardedPort = startPortForwarding(candidate);
            session = candidate;
            localForwardPort = forwardedPort;
            log.info(
                "MySQL SSH tunnel started: {}:{} -> {}:{} via {}@{}:{}",
                ssh.getLocalBindHost(),
                forwardedPort,
                ssh.getRemoteBindHost(),
                ssh.getRemoteBindPort(),
                ssh.getUser(),
                ssh.getHost(),
                ssh.getPort()
            );
            return forwardedPort;
        } catch (JSchException ex) {
            if (candidate != null && candidate.isConnected()) {
                candidate.disconnect();
            }
            throw new IllegalStateException("Failed to start MySQL SSH tunnel", ex);
        }
    }

    int startPortForwarding(Session session) throws JSchException {
        int configuredPort = ssh.getLocalBindPort();
        try {
            return session.setPortForwardingL(
                ssh.getLocalBindHost(),
                configuredPort,
                ssh.getRemoteBindHost(),
                ssh.getRemoteBindPort()
            );
        } catch (JSchException ex) {
            if (!shouldRetryWithRandomLocalPort(configuredPort, ex)) {
                throw ex;
            }
            log.warn(
                "Configured SSH local bind port {} is already in use. Retrying with a random local port.",
                configuredPort
            );
            return session.setPortForwardingL(
                ssh.getLocalBindHost(),
                0,
                ssh.getRemoteBindHost(),
                ssh.getRemoteBindPort()
            );
        }
    }

    static boolean shouldRetryWithRandomLocalPort(int configuredPort, JSchException ex) {
        if (configuredPort <= 0) {
            return false;
        }
        if (findCause(ex, BindException.class) != null) {
            return true;
        }
        String message = ex.getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains("cannot be bound");
    }

    @Override
    public synchronized void close() {
        if (session == null) {
            return;
        }
        Session current = session;
        Integer forwardedPort = localForwardPort;
        session = null;
        localForwardPort = null;
        if (current.isConnected()) {
            current.disconnect();
        }
        log.info(
            "MySQL SSH tunnel closed: {}:{}",
            ssh.getLocalBindHost(),
            forwardedPort == null ? ssh.getLocalBindPort() : forwardedPort
        );
    }

    private static void requireText(String value, String envName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(envName + " must be configured when SSH_ENABLED=true");
        }
    }

    private static <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }
}
