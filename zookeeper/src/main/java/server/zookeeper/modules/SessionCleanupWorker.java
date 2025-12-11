package server.zookeeper.modules;

import org.apache.ratis.protocol.ClientId;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.protocol.RaftClientRequest;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.zookeeper.proto.MessageType;
import server.zookeeper.proto.MessageWrapper;
import server.zookeeper.proto.auth.AuthOperationType;
import server.zookeeper.proto.auth.AuthRequest;
import server.zookeeper.proto.session.Session;

import java.io.Closeable;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Background worker that periodically checks for expired sessions and invalidates them.
 */
public class SessionCleanupWorker implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(SessionCleanupWorker.class);

    private static final long CLEANUP_INTERVAL_SECONDS = 10; // Run cleanup every 10 seconds

    private final SessionManager sessionManager;
    private ScheduledExecutorService scheduler;

    private RaftServer raftServer;
    private RaftGroupId raftGroupId;
    private final ClientId clientId = ClientId.randomId();
    private final AtomicLong callId = new AtomicLong();

    public SessionCleanupWorker(SessionManager sessionManager) {
        this.sessionManager = sessionManager;

    }

    public void setRaftInfo(RaftServer raftServer, RaftGroupId raftGroupId) {
        this.raftServer = raftServer;
        this.raftGroupId = raftGroupId;
    }

    public void start() {
        if (scheduler != null && !scheduler.isShutdown()) {
            LOG.info("Session cleanup worker already running.");
            return;
        }

        LOG.info("Starting session cleanup worker with interval: {} seconds", CLEANUP_INTERVAL_SECONDS);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "session-cleanup-worker");
            t.setDaemon(true);
            return t;
        });

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
            List<Session> expiredSessions = sessionManager.getExpiredSessions();

            if (expiredSessions.isEmpty()) {
                LOG.debug("No expired sessions found");
                return;
            }
            LOG.info("Found {} expired sessions. Submitting LOGOUT requests...", expiredSessions.size());
            for (Session session : expiredSessions) {
                submitLogoutRequest(session.getSessionToken());
            }
        } catch (Exception e) {
            LOG.error("Error during session cleanup", e);
        }
    }

    private void submitLogoutRequest(String sessionToken) {
        try {
            AuthRequest authRequest = AuthRequest.newBuilder()
                    .setOperation(AuthOperationType.LOGOUT)
                    .setSessionToken(sessionToken)
                    .build();

            MessageWrapper wrapper = MessageWrapper.newBuilder()
                    .setType(MessageType.AUTH)
                    .setPayload(authRequest.toByteString())
                    .setSessionToken(sessionToken)
                    .build();

            Message message = Message.valueOf(ByteString.copyFrom(wrapper.toByteArray()));

            RaftClientRequest request = RaftClientRequest.newBuilder()
                    .setClientId(clientId)
                    .setServerId(raftServer.getId())
                    .setGroupId(raftGroupId)
                    .setCallId(callId.incrementAndGet())
                    .setMessage(message)
                    .setType(RaftClientRequest.writeRequestType())
                    .build();

            raftServer.submitClientRequest(request);
            LOG.info("Submitted LOGOUT request for session with token hash: {}", sessionToken.hashCode());
        } catch (Exception e) {
            LOG.error("Failed to submit LOGOUT request for session: {}", sessionToken, e);
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
