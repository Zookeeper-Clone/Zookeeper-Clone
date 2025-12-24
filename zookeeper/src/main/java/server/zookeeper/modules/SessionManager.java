package server.zookeeper.modules;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.zookeeper.DB.DataBase;
import server.zookeeper.DB.SessionRepository;
import server.zookeeper.proto.session.EphemeralEntry;
import server.zookeeper.proto.session.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SessionManager {
    private final SessionRepository sessionRepository;
    private final DataBase db;
    private static final long SESSION_TIMEOUT_NS = TimeUnit.MINUTES.toNanos(2); // 2 minutes in nanoseconds
    private static final Logger LOG = LoggerFactory.getLogger(SessionManager.class);

    public SessionManager(SessionRepository sessionRepository, DataBase db) {
        this.sessionRepository = sessionRepository;
        this.db = db;
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
        if (token == null || token.isEmpty())
            return false;

        Optional<Session> sessionOpt = sessionRepository.getSession(token);
        if (sessionOpt.isEmpty()) {
            LOG.debug("Session with token {} not found", token);
            return false;
        }

        Session session = sessionOpt.get();
        if (!session.getIsValid())
            return false;

        long now = System.nanoTime();
        if (now - session.getLastHeartbeatTime() > SESSION_TIMEOUT_NS) {
            LOG.debug("Session with token {} has expired", token);
            invalidateSession(token);
            return false;
        }
        LOG.info("Session with token {} is valid", token);
        return true;
    }

    public void extendSession(String token) {
        Optional<Session> sessionOpt = sessionRepository.getSession(token);
        if (sessionOpt.isEmpty()) {
            LOG.warn("Cannot extend session. Session with token {} not found", token);
            return;
        }

        LOG.info("Extending session with token {}", token);
        Session updated = sessionOpt.get().toBuilder()
                .setLastHeartbeatTime(System.nanoTime())
                .build();
        sessionRepository.saveSession(updated);
    }

    public void addEphemeralEntry(String sessionToken, String key, String directory) {
        Optional<Session> sessionOpt = sessionRepository.getSession(sessionToken);
        if (sessionOpt.isEmpty()) {
            LOG.error("Cannot add ephemeral entry. Session with token {} not found", sessionToken);
            return;
        }

        Session session = sessionOpt.get();
        EphemeralEntry entry = EphemeralEntry.newBuilder()
                .setKey(key)
                .setDirectory(directory == null ? "" : directory)
                .build();

        Session updatedSession = session.toBuilder()
                .addEphemeralEntries(entry)
                .build();

        sessionRepository.saveSession(updatedSession);
        LOG.info("Added ephemeral entry {}/{} to session {}", directory, key, sessionToken);
    }

    public void invalidateSession(String token) {
        sessionRepository.getSession(token).ifPresentOrElse(
                session -> {
                    session.getEphemeralEntriesList()
                            .forEach(entry -> deleteEphemeralEntry(entry, token));

                    sessionRepository.deleteSession(token);
                },
                () -> LOG.warn("Cannot invalidate session. Session with token {} not found", token));
    }

    private void deleteEphemeralEntry(EphemeralEntry entry, String token) {
        String key = entry.getKey();
        String dir = entry.getDirectory();
        byte[] keyBytes = key.getBytes();

        if (dir.isEmpty()) {
            db.delete(keyBytes);
        } else {
            db.delete(keyBytes, dir);
        }

        LOG.info("Deleted ephemeral entry {}/{} for session {}", dir, key, token);
    }

    public List<Session> getExpiredSessions() {
        long now = System.nanoTime();

        return sessionRepository.getAllSessions().stream()
                .filter(session -> now - session.getLastHeartbeatTime() > SESSION_TIMEOUT_NS)
                .collect(Collectors.toList());
    }
}
