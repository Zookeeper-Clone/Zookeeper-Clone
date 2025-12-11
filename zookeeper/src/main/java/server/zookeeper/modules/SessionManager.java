package server.zookeeper.modules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.zookeeper.DB.SessionRepository;
import server.zookeeper.proto.session.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SessionManager {
    private final SessionRepository sessionRepository;
    private static final long SESSION_TIMEOUT_NS = TimeUnit.MINUTES.toNanos(2); // 2 minutes in nanoseconds
    private static final Logger LOG = LoggerFactory.getLogger(SessionManager.class);

    public SessionManager(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public String createSession(String userEmail) {
        return createSession(userEmail, UUID.randomUUID().toString());
    }

    public String createSession(String userEmail, String token) {
        long now = System.nanoTime();

        Session session = Session.newBuilder()
                .setSessionToken(token)
                .setUserEmail(userEmail)
                .setCreationTime(now)
                .setLastHeartbeatTime(now)
                .setIsValid(true)
                .build();

        sessionRepository.saveSession(session);
        LOG.debug("Created new session for user {}: {}", userEmail, token);
        return token;
    }

    public boolean validateSession(String token) {
        LOG.debug("Validating session with token {}", token);
        if (token == null || token.isEmpty()) return false;

        Optional<Session> sessionOpt = sessionRepository.getSession(token);
        if (sessionOpt.isEmpty()) {
            LOG.debug("Session with token {} not found", token);
            return false;
        }

        Session session = sessionOpt.get();
        if (!session.getIsValid()) return false;

        long now = System.nanoTime();
        if (now - session.getLastHeartbeatTime() > SESSION_TIMEOUT_NS) {
            LOG.debug("Session with token {} has expired", token);
            invalidateSession(token);
            return false;
        }
        LOG.info("Session with token {} is valid", token);
        return true;
    }

    public boolean extendSession(String token) {
        Optional<Session> sessionOpt = sessionRepository.getSession(token);
        if (sessionOpt.isEmpty()) {
            LOG.warn("Cannot extend session. Session with token {} not found", token);
            return false;
        }

        LOG.info("Extending session with token {}", token);
        Session updated = sessionOpt.get().toBuilder()
                .setLastHeartbeatTime(System.nanoTime())
                .build();
        sessionRepository.saveSession(updated);
        return true;
    }

    public void invalidateSession(String token) {
        sessionRepository.deleteSession(token);
    }

    public List<Session> getExpiredSessions() {
        long now = System.nanoTime();
        List<Session> sessions = sessionRepository.getAllSessions();
        List<Session> expired = new ArrayList<>();

        for (Session session : sessions) {
            long lastHeartbeat = session.getLastHeartbeatTime();
            if (now - lastHeartbeat > SESSION_TIMEOUT_NS) {
                expired.add(session);
            }
        }
        return expired;
    }
}
