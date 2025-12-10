package webserver.zookeeper.zookeeper_webserver.services;

import client.zookeeper.ZookeeperClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import webserver.zookeeper.zookeeper_webserver.dto.auth.AuthResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    @Mock
    private ZookeeperClient zookeeperClient;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        authService = new AuthService(zookeeperClient);
    }

    @Test
    void testRegisterSuccess() {
        when(zookeeperClient.register("test@example.com", "Password123"))
                .thenReturn(ZookeeperClient.AuthenticationResult.success("Registered", "token123"));

        AuthResult result = authService.register("test@example.com", "Password123");

        assertTrue(result.success());
        assertEquals("Registered", result.message());
        assertEquals("token123", result.sessionToken());

        verify(zookeeperClient).register("test@example.com", "Password123");
    }

    @Test
    void testRegisterFailure() {
        when(zookeeperClient.register("test@example.com", "Password123"))
                .thenReturn(ZookeeperClient.AuthenticationResult.failure("Already exists"));

        AuthResult result = authService.register("test@example.com", "Password123");

        assertFalse(result.success());
        assertEquals("Already exists", result.message());
        assertNull(result.sessionToken());

        verify(zookeeperClient).register("test@example.com", "Password123");
    }

    @Test
    void testLoginSuccess() {
        when(zookeeperClient.login("user@example.com", "Password123"))
                .thenReturn(ZookeeperClient.AuthenticationResult.success("Logged in", "session456"));

        AuthResult result = authService.login("user@example.com", "Password123");

        assertTrue(result.success());
        assertEquals("Logged in", result.message());
        assertEquals("session456", result.sessionToken());

        verify(zookeeperClient).login("user@example.com", "Password123");
    }

    @Test
    void testLoginFailure() {
        when(zookeeperClient.login("user@example.com", "Password123"))
                .thenReturn(ZookeeperClient.AuthenticationResult.failure("Invalid credentials"));

        AuthResult result = authService.login("user@example.com", "Password123");

        assertFalse(result.success());
        assertEquals("Invalid credentials", result.message());
        assertNull(result.sessionToken());

        verify(zookeeperClient).login("user@example.com", "Password123");
    }
}
