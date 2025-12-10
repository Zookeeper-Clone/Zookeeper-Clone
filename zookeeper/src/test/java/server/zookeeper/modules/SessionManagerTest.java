package server.zookeeper.modules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import server.zookeeper.DB.SessionRepository;
import server.zookeeper.proto.session.Session;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionManager Tests")
class SessionManagerTest {

    @Mock
    private SessionRepository sessionRepository;

    private SessionManager sessionManager;

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager(sessionRepository);
    }

    @Test
    @DisplayName("Should create session and return token")
    void createSession_success() {
        String email = "test@example.com";
        String token = sessionManager.createSession(email);

        assertNotNull(token);
        assertFalse(token.isEmpty());

        ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).saveSession(sessionCaptor.capture());

        Session savedSession = sessionCaptor.getValue();
        assertEquals(email, savedSession.getUserEmail());
        assertEquals(token, savedSession.getSessionToken());
        assertTrue(savedSession.getIsValid());
    }

    @Test
    @DisplayName("Should validate active session")
    void validateSession_valid() {
        String token = "validToken";
        Session session = Session.newBuilder()
                .setSessionToken(token)
                .setIsValid(true)
                .setLastHeartbeatTime(System.currentTimeMillis())
                .build();

        when(sessionRepository.getSession(token)).thenReturn(Optional.of(session));

        assertTrue(sessionManager.validateSession(token));
    }

    @Test
    @DisplayName("Should invalidate expired session")
    void validateSession_expired() {
        String token = "expiredToken";
        // 5 minutes ago (timeout is 2)
        long past = System.currentTimeMillis() - (5 * 60 * 1000);
        Session session = Session.newBuilder()
                .setSessionToken(token)
                .setIsValid(true)
                .setLastHeartbeatTime(past)
                .build();

        when(sessionRepository.getSession(token)).thenReturn(Optional.of(session));

        assertFalse(sessionManager.validateSession(token));
        verify(sessionRepository).deleteSession(token);
    }

    @Test
    @DisplayName("Should fail validation for invalid session flag")
    void validateSession_invalidFlag() {
        String token = "invalidToken";
        Session session = Session.newBuilder()
                .setSessionToken(token)
                .setIsValid(false)
                .build();

        when(sessionRepository.getSession(token)).thenReturn(Optional.of(session));

        assertFalse(sessionManager.validateSession(token));
    }

    @Test
    @DisplayName("Should fail validation for non-existent session")
    void validateSession_notFound() {
        when(sessionRepository.getSession(anyString())).thenReturn(Optional.empty());
        assertFalse(sessionManager.validateSession("missing"));
    }

    @Test
    @DisplayName("Should refresh session heartbeat")
    void refreshSession_success() {
        String token = "token";
        long oldTime = System.currentTimeMillis() - 10000;
        Session session = Session.newBuilder()
                .setSessionToken(token)
                .setLastHeartbeatTime(oldTime)
                .build();

        when(sessionRepository.getSession(token)).thenReturn(Optional.of(session));

        sessionManager.refreshSession(token);

        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).saveSession(captor.capture());
        assertTrue(captor.getValue().getLastHeartbeatTime() > oldTime);
    }
}