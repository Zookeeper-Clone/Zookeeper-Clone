package server.zookeeper.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private GoogleOAuthClient googleOAuthClient;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, sessionRepository, googleOAuthClient);
    }

    @Test
    void testSignUp_NewUser() throws Exception {
        // Arrange
        String idToken = "valid-id-token";
        GoogleOAuthClient.GoogleUserInfo googleUserInfo = new GoogleOAuthClient.GoogleUserInfo(
                "google123", "user@example.com", "Test User", "https://pic.url"
        );

        when(googleOAuthClient.verifyIdToken(idToken)).thenReturn(googleUserInfo);
        when(userRepository.findByGoogleId("google123")).thenReturn(null);

        // Act
        AuthService.AuthResult result = authService.authenticateWithGoogle(idToken);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getUser());
        assertNotNull(result.getSessionId());
        assertEquals("google123", result.getUser().getGoogleId());
        assertEquals("user@example.com", result.getUser().getEmail());

        // Verify user was saved
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals("google123", savedUser.getGoogleId());

        // Verify session was saved
        verify(sessionRepository).save(any(Session.class));
    }

    @Test
    void testSignIn_ExistingUser() throws Exception {
        // Arrange
        String idToken = "valid-id-token";
        User existingUser = new User("google123", "user@example.com", "Test User", "https://pic.url");
        existingUser.setCreatedAt(Instant.now().minusSeconds(86400)); // Created 1 day ago

        GoogleOAuthClient.GoogleUserInfo googleUserInfo = new GoogleOAuthClient.GoogleUserInfo(
                "google123", "user@example.com", "Test User", "https://pic.url"
        );

        when(googleOAuthClient.verifyIdToken(idToken)).thenReturn(googleUserInfo);
        when(userRepository.findByGoogleId("google123")).thenReturn(existingUser);

        // Act
        AuthService.AuthResult result = authService.authenticateWithGoogle(idToken);

        // Assert
        assertNotNull(result);
        assertEquals(existingUser.getId(), result.getUser().getId());

        // Verify lastLoginAt was updated
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertNotNull(userCaptor.getValue().getLastLoginAt());

        // Verify session was created
        verify(sessionRepository).save(any(Session.class));
    }

    @Test
    void testValidateSession_ValidSession() throws Exception {
        // Arrange
        String sessionId = "valid-session-id";
        String userId = "user123";

        Session session = new Session(userId);
        session.setSessionId(sessionId);

        User user = new User("google123", "user@example.com", "Test User", null);
        user.setId(userId);

        when(sessionRepository.findBySessionId(sessionId)).thenReturn(session);
        when(userRepository.findById(userId)).thenReturn(user);

        // Act
        User result = authService.validateSession(sessionId);

        // Assert
        assertNotNull(result);
        assertEquals(userId, result.getId());
        assertEquals("user@example.com", result.getEmail());
    }

    @Test
    void testValidateSession_SessionNotFound() throws Exception {
        // Arrange
        String sessionId = "invalid-session-id";
        when(sessionRepository.findBySessionId(sessionId)).thenReturn(null);

        // Act & Assert
        AuthService.UnauthorizedException exception = assertThrows(
                AuthService.UnauthorizedException.class,
                () -> authService.validateSession(sessionId)
        );
        assertEquals("Session not found", exception.getMessage());
    }

    @Test
    void testValidateSession_ExpiredSession() throws Exception {
        // Arrange
        String sessionId = "expired-session-id";
        Session expiredSession = new Session("user123");
        expiredSession.setSessionId(sessionId);
        expiredSession.setExpiresAt(Instant.now().minusSeconds(3600)); // Expired 1 hour ago

        when(sessionRepository.findBySessionId(sessionId)).thenReturn(expiredSession);

        // Act & Assert
        AuthService.UnauthorizedException exception = assertThrows(
                AuthService.UnauthorizedException.class,
                () -> authService.validateSession(sessionId)
        );
        assertEquals("Session expired", exception.getMessage());

        // Verify expired session was deleted
        verify(sessionRepository).delete(sessionId);
    }

    @Test
    void testValidateSession_UserNotFound() throws Exception {
        // Arrange
        String sessionId = "valid-session-id";
        Session session = new Session("nonexistent-user");
        session.setSessionId(sessionId);

        when(sessionRepository.findBySessionId(sessionId)).thenReturn(session);
        when(userRepository.findById(anyString())).thenReturn(null);

        // Act & Assert
        assertThrows(AuthService.UnauthorizedException.class,
                () -> authService.validateSession(sessionId));
    }

    @Test
    void testSignOut() throws Exception {
        // Arrange
        String sessionId = "session-to-delete";

        // Act
        authService.signOut(sessionId);

        // Assert
        verify(sessionRepository).delete(sessionId);
    }
}