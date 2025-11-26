package server.zookeeper.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import server.zookeeper.DB.CRocksDB;

/**
 * Data access layer for Session entities with RocksDB persistence using column families.
 *
 * Storage schema:
 * - Column family: "sessions"
 * - Key: "session:<sessionId>" → Session JSON
 *
 * Using a dedicated column family provides better organization and allows
 * for independent configuration of storage parameters for session data.
 * Sessions are stored with a simple key-value structure since session IDs
 * are already globally unique UUIDs that serve as natural primary keys.
 *
 * Operations:
 *
 * save(session):
 * - Serializes Session to JSON using Jackson
 * - Stores in "sessions" CF: "session:<sessionId>" → JSON
 * - Used on login to create new sessions
 *
 * findBySessionId(sessionId):
 * - Queries "sessions" CF: "session:<sessionId>"
 * - Deserializes JSON to Session object
 * - Used by AuthService to validate sessions on protected requests
 * - Returns null if session doesn't exist
 *
 * delete(sessionId):
 * - Removes from "sessions" CF: "session:<sessionId>"
 * - Used on logout and when cleaning up expired sessions
 *
 * exists(sessionId):
 * - Checks if key exists in "sessions" CF
 * - Lightweight existence check without deserializing full object
 *
 * Uses Jackson with JavaTimeModule for Instant field serialization.
 */
public class SessionRepository {
    private final CRocksDB db;
    private final ObjectMapper objectMapper;

    private static final String COLUMN_FAMILY = "sessions";  // ← ADD THIS
    private static final String SESSION_PREFIX = "session:";

    public SessionRepository(CRocksDB db) {
        this.db = db;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public void save(Session session) throws Exception {
        // Serialize session to JSON
        String sessionJson = objectMapper.writeValueAsString(session);

        // Save in "sessions" column family: "session:{sessionId}" -> Session JSON
        db.put(
                (SESSION_PREFIX + session.getSessionId()).getBytes(),
                sessionJson.getBytes(),
                COLUMN_FAMILY  // ← ADD THIS PARAMETER
        );
    }

    public Session findBySessionId(String sessionId) throws Exception {
        byte[] key = (SESSION_PREFIX + sessionId).getBytes();
        byte[] value = db.get(key, COLUMN_FAMILY);  // ← ADD COLUMN_FAMILY PARAMETER

        if (value == null) {
            return null;
        }

        String json = new String(value);
        return objectMapper.readValue(json, Session.class);
    }

    public void delete(String sessionId) {
        db.delete(
                (SESSION_PREFIX + sessionId).getBytes(),
                COLUMN_FAMILY  // ← ADD THIS PARAMETER
        );
    }

    public boolean exists(String sessionId) {
        byte[] key = (SESSION_PREFIX + sessionId).getBytes();
        byte[] value = db.get(key, COLUMN_FAMILY);  // ← ADD COLUMN_FAMILY PARAMETER
        return value != null;
    }
}