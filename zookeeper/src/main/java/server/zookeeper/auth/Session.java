package server.zookeeper.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain model representing an authenticated user session.
 *
 * Fields:
 * - sessionId: Unique UUID identifying this session
 * - userId: ID of the User this session belongs to
 * - createdAt: Timestamp when session was created
 * - expiresAt: Timestamp when session expires (default: 7 days from creation)
 *
 * Lifecycle:
 * - Created on successful Google OAuth sign-in/sign-up
 * - Validated on each protected endpoint request
 * - Deleted on logout or when expired
 *
 * Expiration:
 * - Default expiration: 7 days from creation
 * - isExpired() checks if current time is after expiresAt
 * - Expired sessions are automatically deleted when validation is attempted
 *
 * Security:
 * - Session ID is treated as a bearer token
 * - Should be transmitted securely (HTTPS)
 * - Should be stored securely on client (httpOnly cookies recommended)
 */
public class Session {
    private String sessionId;
    private String userId;
    private Instant createdAt;
    private Instant expiresAt;

    public Session() {
        this.sessionId = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
        // Default 7 days expiration
        this.expiresAt = Instant.now().plusSeconds(7 * 24 * 60 * 60);
    }

    public Session(String userId) {
        this();
        this.userId = userId;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    // Getters and setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}