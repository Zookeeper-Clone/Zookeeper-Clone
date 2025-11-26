package server.zookeeper.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import server.zookeeper.DB.CRocksDB;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SessionRepositoryTest {

    @Mock
    private CRocksDB db;

    private SessionRepository sessionRepository;

    @BeforeEach
    void setUp() {
        sessionRepository = new SessionRepository(db);
        reset(db);
    }

    @Test
    void testSave_CreatesSession() throws Exception {
        // Arrange
        Session session = new Session("user-id-123");
        session.setSessionId("session-id-456");

        // Act
        sessionRepository.save(session);

        // Assert
        ArgumentCaptor<byte[]> keyCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<byte[]> valueCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> cfCaptor = ArgumentCaptor.forClass(String.class);

        verify(db).put(keyCaptor.capture(), valueCaptor.capture(), cfCaptor.capture());

        String key = new String(keyCaptor.getValue());
        String value = new String(valueCaptor.getValue());
        String columnFamily = cfCaptor.getValue();

        assertTrue(key.startsWith("session:"));
        assertTrue(value.contains("session-id-456"));
        assertTrue(value.contains("user-id-123"));
        assertEquals("sessions", columnFamily);  // ← Verify correct CF
    }

    @Test
    void testFindBySessionId_ReturnsSession() throws Exception {
        String sessionId = "session-id-456";
        String sessionJson = "{\"sessionId\":\"session-id-456\",\"userId\":\"user-id-123\"," +
                "\"createdAt\":\"2024-11-25T10:00:00.000Z\"," +  // ← Added .000
                "\"expiresAt\":\"2024-12-02T10:00:00.000Z\"}";   // ← Added .000

        when(db.get(any(byte[].class), eq("sessions")))
                .thenReturn(sessionJson.getBytes());

        Session session = sessionRepository.findBySessionId(sessionId);

        assertNotNull(session);
        assertEquals("session-id-456", session.getSessionId());
        assertEquals("user-id-123", session.getUserId());
    }

    @Test
    void testFindBySessionId_ReturnsNull_WhenNotFound() throws Exception {
        // Arrange
        when(db.get(any(byte[].class), eq("sessions")))  // ← Add CF parameter
                .thenReturn(null);

        // Act
        Session session = sessionRepository.findBySessionId("nonexistent-id");

        // Assert
        assertNull(session);
    }

    @Test
    void testDelete_RemovesSession() {
        // Arrange
        String sessionId = "session-id-456";

        // Act
        sessionRepository.delete(sessionId);

        // Assert
        ArgumentCaptor<byte[]> keyCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> cfCaptor = ArgumentCaptor.forClass(String.class);

        verify(db).delete(keyCaptor.capture(), cfCaptor.capture());

        String key = new String(keyCaptor.getValue());
        assertEquals("session:session-id-456", key);
        assertEquals("sessions", cfCaptor.getValue());  // ← Verify correct CF
    }

    @Test
    void testExists_ReturnsTrue_WhenSessionExists() {
        // Arrange
        String sessionId = "session-id-456";
        when(db.get(any(byte[].class), eq("sessions")))  // ← Add CF parameter
                .thenReturn("{}".getBytes());

        // Act
        boolean exists = sessionRepository.exists(sessionId);

        // Assert
        assertTrue(exists);
    }

    @Test
    void testExists_ReturnsFalse_WhenSessionDoesNotExist() {
        // Arrange
        when(db.get(any(byte[].class), eq("sessions")))  // ← Add CF parameter
                .thenReturn(null);

        // Act
        boolean exists = sessionRepository.exists("nonexistent-id");

        // Assert
        assertFalse(exists);
    }
}