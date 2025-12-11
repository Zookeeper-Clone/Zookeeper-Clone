package server.zookeeper.modules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Background worker that periodically checks for expired sessions and invalidates them.
 */
public class SessionCleanupWorker implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(SessionCleanupWorker.class);

    private static final long CLEANUP_INTERVAL_SECONDS = 10; // Run cleanup every 10 seconds
    
    private final SessionManager sessionManager;
    private final ScheduledExecutorService scheduler;

    public SessionCleanupWorker(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "session-cleanup-worker");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        LOG.info("Starting session cleanup worker with interval: {} seconds", CLEANUP_INTERVAL_SECONDS);
        scheduler.scheduleAtFixedRate(
                this::cleanupExpiredSessions,
                CLEANUP_INTERVAL_SECONDS,
                CLEANUP_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    private void cleanupExpiredSessions() {
        try {
            LOG.debug("Running session cleanup...");
            int expiredCount = sessionManager.cleanupExpiredSessions();

            if (expiredCount > 0) {
                LOG.info("Cleaned up {} expired sessions", expiredCount);
            } else {
                LOG.info("No expired sessions found");
            }
        } catch (Exception e) {
            LOG.error("Error during session cleanup", e);
        }
    }

    public void stop() {
        LOG.info("Stopping session cleanup worker");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        stop();
    }
}
