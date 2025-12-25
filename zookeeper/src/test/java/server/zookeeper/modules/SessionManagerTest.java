package server.zookeeper.modules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import server.zookeeper.DB.SessionRepository;
import server.zookeeper.proto.session.EphemeralEntry;
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
    @Mock
    private server.zookeeper.DB.DataBase db;

    private SessionManager sessionManager;

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager(sessionRepository, db);
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
                .setLastHeartbeatTime(System.nanoTime())
                .build();

        when(sessionRepository.getSession(token)).thenReturn(Optional.of(session));

        assertTrue(sessionManager.validateSession(token));
    }

    @Test
    @DisplayName("Should invalidate expired session")
    void validateSession_expired() {
        String token = "expiredToken";
        // 5 minutes ago in nanoseconds (timeout is 2 minutes)
        long past = System.nanoTime() - (5L * 60 * 1_000_000_000L);
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
    @DisplayName("Should extend session heartbeat")
    void extendSession_success() {
        String token = "token";
        long oldTime = System.nanoTime() - 10_000_000_000L; // 10 seconds ago in nanoseconds
        Session session = Session.newBuilder()
                .setSessionToken(token)
                .setLastHeartbeatTime(oldTime)
                .build();

        when(sessionRepository.getSession(token)).thenReturn(Optional.of(session));

        sessionManager.extendSession(token);

        ArgumentCaptor<Session> captor = ArgumentCaptor.forClass(Session.class);
        verify(sessionRepository).saveSession(captor.capture());
        assertTrue(captor.getValue().getLastHeartbeatTime() > oldTime);
    }

    @Test
    @DisplayName("Should delete ephemeral entries when invalidating session")
    void invalidateSession_deletesEphemeralEntries() {
        String token = "tokenWithEphemeral";
        String key = "ephemeralKey";
        String dir = "temp";

        EphemeralEntry entry = EphemeralEntry.newBuilder()
                .setKey(key)
                .setDirectory(dir)
                .build();

        Session session = Session.newBuilder()
                .setSessionToken(token)
                .addEphemeralEntries(entry)
                .build();

        when(sessionRepository.getSession(token)).thenReturn(Optional.of(session));

        sessionManager.invalidateSession(token);

        verify(db).delete(key.getBytes(), dir);
        verify(sessionRepository).deleteSession(token);
    }
}