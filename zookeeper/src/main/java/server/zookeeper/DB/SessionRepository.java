package server.zookeeper.DB;

import com.google.protobuf.InvalidProtocolBufferException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.zookeeper.proto.session.Session;
import server.zookeeper.util.ReservedDirectories;

import java.nio.charset.StandardCharsets;
import java.util.Optional;


public class SessionRepository {
    private static final Logger LOG = LoggerFactory.getLogger(SessionRepository.class);
    private static final String SESSION_DIRECTORY = ReservedDirectories.SESSION_DIRECTORY;
    private final DataBase database;

    public SessionRepository(DataBase database) {
        if (database == null) {
            throw new IllegalArgumentException("Database cannot be null");
        }
        this.database = database;
        LOG.debug("SessionRepository initialized with column family: {}", SESSION_DIRECTORY);
    }

    public void saveSession(Session session) {
        if (session == null) {
            throw new IllegalArgumentException("Session cannot be null");
        }
        String token = session.getSessionToken();
        LOG.info("Saving session with token hash: {}", token.hashCode());
        try {
            byte[] key = tokenToKey(token);
            byte[] value = session.toByteArray();
            database.put(key, value, SESSION_DIRECTORY);
        } catch (Exception e) {
            LOG.error("Failed to save session", e);
            throw new RuntimeException("Failed to save session", e);
        }
    }

    public Optional<Session> getSession(String token) {
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }
        try {
            byte[] key = tokenToKey(token);
            byte[] value = database.get(key, SESSION_DIRECTORY);

            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(Session.parseFrom(value));
        } catch (InvalidProtocolBufferException e) {
            LOG.error("Corrupted session data", e);
            return Optional.empty();
        } catch (Exception e) {
            LOG.error("Failed to retrieve session", e);
            throw new RuntimeException("Failed to retrieve session", e);
        }
    }

    public void deleteSession(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        LOG.info("Deleting session with token hash: {}", token.hashCode());
        try {
            byte[] key = tokenToKey(token);
            database.delete(key, SESSION_DIRECTORY);
        } catch (Exception e) {
            LOG.error("Failed to delete session", e);
            throw new RuntimeException("Failed to delete session", e);
        }
    }

    private byte[] tokenToKey(String token) {
        return token.getBytes(StandardCharsets.UTF_8);
    }
}
