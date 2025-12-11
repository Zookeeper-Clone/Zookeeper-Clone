package client.zookeeper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.*;

public class SessionManager implements AutoCloseable{
    private static final Logger LOG = LoggerFactory.getLogger(SessionManager.class);
    private String sessionToken;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> heartbeatTask;

    private static final long HEARTBEAT_INTERVAL_SECONDS = 30;

    public synchronized void startSession(String token, Runnable heartbeatAction) {
        this.sessionToken = token;
        stopHeartbeat(); // Stop any existing heartbeat
        this.heartbeatTask = scheduler.scheduleAtFixedRate(
                                    heartbeatAction,
                                    HEARTBEAT_INTERVAL_SECONDS,
                                    HEARTBEAT_INTERVAL_SECONDS,
                                    TimeUnit.SECONDS);
        LOG.info("Session started with token: {}. Heartbeat scheduled", token);
    }

    public synchronized void invalidateSession() {
        this.sessionToken = null;
        stopHeartbeat();
        LOG.info("Session invalidated and heartbeat stopped");
    }

    private synchronized void stopHeartbeat() {
        if (heartbeatTask != null && !heartbeatTask.isCancelled()) {
            heartbeatTask.cancel(true);
            LOG.info("Heartbeat task cancelled");
        }
    }

    public synchronized Optional<String> getToken() {
        return Optional.ofNullable(sessionToken);
    }

    public synchronized boolean isAuthenticated() {
        return sessionToken != null && !sessionToken.isEmpty();
    }

    @Override
    public void close() {
        invalidateSession();
        scheduler.shutdown();
    }
}
