package server.zookeeper.DB;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import server.zookeeper.proto.session.Session;
import server.zookeeper.util.ReservedDirectories;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionRepository Tests")
class SessionRepositoryTest {
    @Mock
    private DataBase mockDatabase;
    private SessionRepository sessionRepository;

    @BeforeEach
    void setUp() {
        sessionRepository = new SessionRepository(mockDatabase);
    }

    @Test
    @DisplayName("Should save session successfully")
    void saveSession_success() {
        Session session = Session.newBuilder()
                .setSessionToken("token123")
                .setUserEmail("test@example.com")
                .setCreationTime(System.currentTimeMillis())
                .setIsValid(true)
                .build();

        sessionRepository.saveSession(session);

        verify(mockDatabase).put(
                eq("token123".getBytes(StandardCharsets.UTF_8)),
                any(byte[].class),
                eq(ReservedDirectories.SESSION_DIRECTORY)
        );
    }

    @Test
    @DisplayName("Should throw exception when saving null session")
    void saveSession_null_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> sessionRepository.saveSession(null));
    }

    @Test
    @DisplayName("Should get session successfully")
    void getSession_success() throws Exception {
        String token = "token123";
        Session expectedSession = Session.newBuilder()
                .setSessionToken(token)
                .setUserEmail("test@example.com")
                .build();

        when(mockDatabase.get(any(byte[].class), eq(ReservedDirectories.SESSION_DIRECTORY)))
                .thenReturn(expectedSession.toByteArray());

        Optional<Session> result = sessionRepository.getSession(token);

        assertTrue(result.isPresent());
        assertEquals(token, result.get().getSessionToken());
        assertEquals("test@example.com", result.get().getUserEmail());
    }

    @Test
    @DisplayName("Should return empty when session not found")
    void getSession_notFound() {
        when(mockDatabase.get(any(byte[].class), eq(ReservedDirectories.SESSION_DIRECTORY)))
                .thenReturn(null);

        Optional<Session> result = sessionRepository.getSession("nonexistent");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should delete session successfully")
    void deleteSession_success() {
        String token = "token123";
        sessionRepository.deleteSession(token);

        verify(mockDatabase).delete(
                eq(token.getBytes(StandardCharsets.UTF_8)),
                eq(ReservedDirectories.SESSION_DIRECTORY)
        );
    }
}