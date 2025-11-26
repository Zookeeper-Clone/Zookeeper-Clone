package server.zookeeper.auth;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain model representing a registered user in the authentication system.
 *
 * Fields:
 * - id: Unique UUID generated on user creation
 * - googleId: Google's unique identifier for this user (from OAuth token)
 * - email: User's email address from Google account
 * - name: User's full name from Google account
 * - pictureUrl: URL to user's Google profile picture
 * - createdAt: Timestamp when user account was created (first sign-up)
 * - lastLoginAt: Timestamp of user's most recent login
 *
 * Lifecycle:
 * - Created during first Google OAuth sign-in (sign-up)
 * - Updated on each subsequent sign-in (lastLoginAt field)
 * - Persisted in RocksDB via UserRepository
 *
 * JSON serialization:
 * - Uses Jackson with JavaTimeModule for Instant serialization
 * - ISO-8601 format: "2025-11-26T10:00:00.000Z"
 */
public class User {
    private String id;
    private String googleId;
    private String email;
    private String name;
    private String pictureUrl;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private Instant createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
    private Instant lastLoginAt;

    public User() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
    }

    public User(String googleId, String email, String name, String pictureUrl) {
        this();
        this.googleId = googleId;
        this.email = email;
        this.name = name;
        this.pictureUrl = pictureUrl;
    }

    // Getters and setters (unchanged)
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getGoogleId() { return googleId; }
    public void setGoogleId(String googleId) { this.googleId = googleId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPictureUrl() { return pictureUrl; }
    public void setPictureUrl(String pictureUrl) { this.pictureUrl = pictureUrl; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}