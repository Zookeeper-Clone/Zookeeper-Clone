package server.zookeeper.modules;

import server.zookeeper.DB.SessionRepository;
import server.zookeeper.proto.session.Session;

import java.util.Optional;
import java.util.UUID;

public class SessionManager {
    private final SessionRepository sessionRepository;
    private static final long SESSION_TIMEOUT_MS = 2 * 60 * 1000; // 2 minutes

    public SessionManager(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public String createSession(String userEmail) {
        String token = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        Session session = Session.newBuilder()
                .setSessionToken(token)
                .setUserEmail(userEmail)
                .setCreationTime(now)
                .setLastHeartbeatTime(now)
                .setIsValid(true)
                .build();

        sessionRepository.saveSession(session);
        return token;
    }

    public boolean validateSession(String token) {
        if (token == null || token.isEmpty()) return false;

        Optional<Session> sessionOpt = sessionRepository.getSession(token);
        if (sessionOpt.isEmpty()) return false;

        Session session = sessionOpt.get();
//        if (!session.getIsValid()) return false;

        //TODO: research about another way instead of using currentTime
        long now = System.currentTimeMillis();
        if (now - session.getLastHeartbeatTime() > SESSION_TIMEOUT_MS) {
            invalidateSession(token);
            return false;
        }

        return true;
    }

    public void refreshSession(String token) {
        Optional<Session> sessionOpt = sessionRepository.getSession(token);
        if (sessionOpt.isPresent()) {
            Session session = sessionOpt.get();
            Session updated = session.toBuilder()
                    .setLastHeartbeatTime(System.currentTimeMillis())
                    .build();
            sessionRepository.saveSession(updated);
        }
    }

    public void invalidateSession(String token) {
        sessionRepository.deleteSession(token);
    }
}
