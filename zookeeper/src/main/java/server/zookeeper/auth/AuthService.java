package server.zookeeper.auth;

import java.time.Instant;

/**
 * Core authentication business logic that orchestrates the authentication system.
 *
 * Key responsibilities:
 *
 * 1. authenticateWithGoogle(idToken):
 *    - Verifies Google ID token using GoogleOAuthClient
 *    - Looks up user by Google ID in UserRepository
 *    - Creates new user if not found (sign-up)
 *    - Updates last login time if user exists (sign-in)
 *    - Creates new session in SessionRepository
 *    - Returns AuthResult with user and session ID
 *
 * 2. validateSession(sessionId):
 *    - Retrieves session from SessionRepository
 *    - Checks if session exists and is not expired
 *    - Retrieves associated user from UserRepository
 *    - Returns User object if valid
 *    - Throws UnauthorizedException if invalid
 *
 * 3. signOut(sessionId):
 *    - Deletes session from SessionRepository
 *
 * This service coordinates between GoogleOAuthClient, UserRepository, and
 * SessionRepository to provide a cohesive authentication API.
 */
public class AuthService {
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final GoogleOAuthClient googleOAuthClient;

    public AuthService(UserRepository userRepository,
                       SessionRepository sessionRepository,
                       GoogleOAuthClient googleOAuthClient) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.googleOAuthClient = googleOAuthClient;
    }

    public AuthResult authenticateWithGoogle(String idToken) throws Exception {
        // Verify the Google ID token and extract user info
        GoogleOAuthClient.GoogleUserInfo googleUserInfo =
                googleOAuthClient.verifyIdToken(idToken);

        // Check if user already exists in database
        User user = userRepository.findByGoogleId(googleUserInfo.getGoogleId());

        if (user == null) {
            // Sign up: Create new user
            user = new User(
                    googleUserInfo.getGoogleId(),
                    googleUserInfo.getEmail(),
                    googleUserInfo.getName(),
                    googleUserInfo.getPictureUrl()
            );
            userRepository.save(user);
        } else {
            // Sign in: Update last login time
            user.setLastLoginAt(Instant.now());
            userRepository.save(user);
        }

        // Create a new session
        Session session = new Session(user.getId());
        sessionRepository.save(session);

        return new AuthResult(user, session.getSessionId());
    }

    public User validateSession(String sessionId) throws Exception {
        // Find session in database
        Session session = sessionRepository.findBySessionId(sessionId);
        if (session == null) {
            throw new UnauthorizedException("Session not found");
        }
        if (session.isExpired()) {
            sessionRepository.delete(sessionId);
            throw new UnauthorizedException("Session expired");
        }

        // Fetch the user for this session
        User user = userRepository.findById(session.getUserId());
        if (user == null) {
            throw new UnauthorizedException("User not found");
        }

        return user;
    }

    public void signOut(String sessionId) throws Exception {
        sessionRepository.delete(sessionId);
    }

    public static class AuthResult {
        private final User user;
        private final String sessionId;

        public AuthResult(User user, String sessionId) {
            this.user = user;
            this.sessionId = sessionId;
        }

        public User getUser() { return user; }
        public String getSessionId() { return sessionId; }
    }

    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }
}