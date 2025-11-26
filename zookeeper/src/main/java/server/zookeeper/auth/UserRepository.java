package server.zookeeper.auth;

import server.zookeeper.DB.CRocksDB;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Data access layer for User entities with RocksDB persistence using column families.
 *
 * Storage schema:
 * - Column family: "users"
 * - Primary key: "user:<userId>" → User JSON
 * - Secondary index: "google_id_index:<googleId>" → userId
 *
 * Using a dedicated column family provides better organization and allows
 * for independent configuration of storage parameters for user data.
 *
 * Operations:
 *
 * save(user):
 * - Serializes User to JSON using Jackson
 * - Stores user record in "users" CF: "user:<userId>" → JSON
 * - Updates index in "users" CF: "google_id_index:<googleId>" → userId
 *
 * findById(id):
 * - Queries "users" CF: "user:<userId>"
 * - Deserializes JSON to User object
 *
 * findByGoogleId(googleId):
 * - First queries index in "users" CF: "google_id_index:<googleId>" → userId
 * - Then queries user record in "users" CF: "user:<userId>" → User JSON
 * - Two-step lookup for efficient Google ID-based authentication
 *
 * delete(id):
 * - Removes both user record and Google ID index entry from "users" CF
 *
 * Uses Jackson with JavaTimeModule for Instant field serialization.
 */
public class UserRepository {

    private final CRocksDB db;
    private final ObjectMapper objectMapper;

    private static final String COLUMN_FAMILY = "users";  // ← ADD THIS
    private static final String USER_PREFIX = "user:";
    private static final String GOOGLE_ID_INDEX_PREFIX = "google_id_index:";

    public UserRepository(CRocksDB db) {
        this.db = db;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public void save(User user) throws Exception {
        String userJson = objectMapper.writeValueAsString(user);

        // Save user record in "users" column family
        db.put(
                (USER_PREFIX + user.getId()).getBytes(),
                userJson.getBytes(),
                COLUMN_FAMILY  // ← ADD THIS PARAMETER
        );

        // Save Google ID index in "users" column family
        if (user.getGoogleId() != null) {
            db.put(
                    (GOOGLE_ID_INDEX_PREFIX + user.getGoogleId()).getBytes(),
                    user.getId().getBytes(),
                    COLUMN_FAMILY  // ← ADD THIS PARAMETER
            );
        }
    }

    public User findById(String id) throws Exception {
        byte[] key = (USER_PREFIX + id).getBytes();
        byte[] value = db.get(key, COLUMN_FAMILY);  // ← ADD COLUMN_FAMILY PARAMETER

        if (value == null) return null;

        return objectMapper.readValue(new String(value), User.class);
    }

    public User findByGoogleId(String googleId) throws Exception {
        byte[] indexKey = (GOOGLE_ID_INDEX_PREFIX + googleId).getBytes();
        byte[] userIdBytes = db.get(indexKey, COLUMN_FAMILY);  // ← ADD COLUMN_FAMILY PARAMETER

        if (userIdBytes == null) return null;

        return findById(new String(userIdBytes));
    }

    public void delete(String id) throws Exception {
        User user = findById(id);
        if (user == null) return;

        if (user.getGoogleId() != null) {
            db.delete(
                    (GOOGLE_ID_INDEX_PREFIX + user.getGoogleId()).getBytes(),
                    COLUMN_FAMILY  // ← ADD THIS PARAMETER
            );
        }

        db.delete(
                (USER_PREFIX + id).getBytes(),
                COLUMN_FAMILY  // ← ADD THIS PARAMETER
        );
    }

    public boolean exists(String id) {
        byte[] value = db.get((USER_PREFIX + id).getBytes(), COLUMN_FAMILY);  // ← ADD COLUMN_FAMILY PARAMETER
        return value != null;
    }
}