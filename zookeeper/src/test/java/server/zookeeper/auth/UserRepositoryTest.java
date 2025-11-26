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
class UserRepositoryTest {

    @Mock
    private CRocksDB db;

    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository = new UserRepository(db);
    }

    @Test
    void testSave_CreatesUserAndIndices() throws Exception {
        User user = new User("google123", "test@example.com", "Test User", "https://pic.url");
        user.setId("user-id-123");

        userRepository.save(user);

        ArgumentCaptor<byte[]> keyCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<byte[]> valueCaptor = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<String> cfCaptor = ArgumentCaptor.forClass(String.class);

        verify(db, times(2)).put(
                keyCaptor.capture(),
                valueCaptor.capture(),
                cfCaptor.capture()
        );

        var keys = keyCaptor.getAllValues();
        var cfs = cfCaptor.getAllValues();

        assertTrue(new String(keys.get(0)).startsWith("user:"));
        assertTrue(new String(keys.get(1)).startsWith("google_id_index:"));

        assertEquals("users", cfs.get(0));
        assertEquals("users", cfs.get(1));

        String userJson = new String(valueCaptor.getAllValues().get(0));
        assertTrue(userJson.contains("google123"));
        assertTrue(userJson.contains("test@example.com"));
    }

    @Test
    void testFindById_ReturnsUser() throws Exception {
        String userId = "user-id-123";
        String userJson = "{\"id\":\"user-id-123\",\"googleId\":\"google123\"," +
                "\"email\":\"test@example.com\",\"name\":\"Test User\"," +
                "\"pictureUrl\":\"https://pic.url\",\"createdAt\":\"2024-11-25T10:00:00.000Z\"}";  // ← Added .000

        when(db.get(any(byte[].class), eq("users")))
                .thenReturn(userJson.getBytes());

        User user = userRepository.findById(userId);

        assertNotNull(user);
        assertEquals("user-id-123", user.getId());
        assertEquals("google123", user.getGoogleId());
        assertEquals("test@example.com", user.getEmail());

        verify(db).get(any(byte[].class), eq("users"));
    }

    @Test
    void testFindById_ReturnsNull_WhenNotFound() throws Exception {
        when(db.get(any(byte[].class), eq("users")))
                .thenReturn(null);

        User user = userRepository.findById("nonexistent-id");

        assertNull(user);
    }

    @Test
    void testFindByGoogleId_ReturnsUser() throws Exception {
        String googleId = "google123";
        String userId = "user-id-123";
        String userJson = "{\"id\":\"user-id-123\",\"googleId\":\"google123\"," +
                "\"email\":\"test@example.com\",\"name\":\"Test User\"," +
                "\"pictureUrl\":null,\"createdAt\":\"2024-11-25T10:00:00.000Z\"}";  // ← Added .000

        when(db.get(any(byte[].class), eq("users")))
                .thenReturn(userId.getBytes(), userJson.getBytes());

        User user = userRepository.findByGoogleId(googleId);

        assertNotNull(user);
        assertEquals("google123", user.getGoogleId());
        assertEquals("user-id-123", user.getId());

        verify(db, times(2)).get(any(byte[].class), eq("users"));
    }

    @Test
    void testDelete_RemovesUserAndIndices() throws Exception {
        String userId = "user-id-123";
        String userJson = "{\"id\":\"user-id-123\",\"googleId\":\"google123\"," +
                "\"email\":\"test@example.com\",\"name\":\"Test User\"," +
                "\"pictureUrl\":null,\"createdAt\":\"2024-11-25T10:00:00.000Z\"}";  // ← Added .000

        when(db.get(any(byte[].class), eq("users")))
                .thenReturn(userJson.getBytes());

        userRepository.delete(userId);

        ArgumentCaptor<String> cfCaptor = ArgumentCaptor.forClass(String.class);
        verify(db, times(2)).delete(any(byte[].class), cfCaptor.capture());

        assertEquals("users", cfCaptor.getAllValues().get(0));
        assertEquals("users", cfCaptor.getAllValues().get(1));
    }

    @Test
    void testExists_ReturnsTrue_WhenUserExists() {
        when(db.get(any(byte[].class), eq("users")))
                .thenReturn("{}".getBytes());

        boolean exists = userRepository.exists("user-id-123");

        assertTrue(exists);
    }

    @Test
    void testExists_ReturnsFalse_WhenUserDoesNotExist() {
        when(db.get(any(byte[].class), eq("users")))
                .thenReturn(null);

        boolean exists = userRepository.exists("nonexistent-id");

        assertFalse(exists);
    }
}