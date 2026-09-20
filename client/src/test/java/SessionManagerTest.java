import client.zookeeper.SessionManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SessionManagerTest {
    @Test
    void testSessionLifecycle() {
        SessionManager sm = new SessionManager();

        // Initial state
        assertFalse(sm.isAuthenticated());
        assertTrue(sm.getToken().isEmpty());

        // Start session
        sm.startSession("token123", () -> {
        });
        assertTrue(sm.isAuthenticated());
        assertEquals("token123", sm.getToken().get());

        // Invalidate session
        sm.invalidateSession();
        assertFalse(sm.isAuthenticated());
        assertTrue(sm.getToken().isEmpty());

        sm.close();
    }

    @Test
    void testStartSessionOverwritesPrevious() {
        SessionManager sm = new SessionManager();
        sm.startSession("tokenA", () -> {
        });
        assertEquals("tokenA", sm.getToken().get());

        sm.startSession("tokenB", () -> {
        });
        assertEquals("tokenB", sm.getToken().get());

        sm.close();
    }

    @Test
    void testStartSessionWithInvalidTokensThrows() {
        SessionManager sm = new SessionManager();
        assertThrows(IllegalArgumentException.class, () -> sm.startSession(null, () -> {}));
        assertThrows(IllegalArgumentException.class, () -> sm.startSession("", () -> {}));
        assertThrows(IllegalArgumentException.class, () -> sm.startSession("   ", () -> {}));
        sm.close();
    }

    @Test
    void testMaskToken() {
        assertEquals("***", SessionManager.maskToken(null));
        assertEquals("***", SessionManager.maskToken(""));
        assertEquals("***", SessionManager.maskToken("abc"));
        assertEquals("***", SessionManager.maskToken("1234"));
        assertEquals("***5678", SessionManager.maskToken("12345678"));
        assertEquals("***abcd", SessionManager.maskToken("super-secret-token-abcd"));
    }
}
