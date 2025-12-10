package server.zookeeper.modules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.zookeeper.DB.SessionRepository;
import server.zookeeper.proto.session.Session;

import java.util.Optional;
import java.util.UUID;

public class SessionManager {
    private final SessionRepository sessionRepository;
    private static final long SESSION_TIMEOUT_MS = 2 * 60 * 1000; // 2 minutes
    private static final Logger LOG = LoggerFactory.getLogger(SessionManager.class);

    public SessionManager(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public String createSession(String userEmail) {
        return createSession(userEmail, UUID.randomUUID().toString());
    }

    public String createSession(String userEmail, String token) {
        long now = System.currentTimeMillis();

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
//        if (!session.getIsValid()) return false;

        //TODO: research about another way instead of using currentTime
        long now = System.currentTimeMillis();
        if (now - session.getLastHeartbeatTime() > SESSION_TIMEOUT_MS) {
            LOG.debug("Session with token {} has expired", token);
            invalidateSession(token);
            return false;
        }
        LOG.info("Session with token {} is valid", token);
        return true;
    }

    public void refreshSession(String token) {
        Optional<Session> sessionOpt = sessionRepository.getSession(token);
        if (sessionOpt.isPresent()) {
            LOG.info("Refreshing session with token {}", token);
            Session session = sessionOpt.get();
            Session updated = session.toBuilder()
                    .setLastHeartbeatTime(System.currentTimeMillis())
                    .build();
            sessionRepository.saveSession(updated);
        } else {
            LOG.warn("Cannot refresh session. Session with token {} not found", token);
        }
    }

    public void invalidateSession(String token) {
        sessionRepository.deleteSession(token);
    }
}
