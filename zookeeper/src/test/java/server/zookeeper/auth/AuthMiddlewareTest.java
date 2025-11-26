package server.zookeeper.auth;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthMiddlewareTest {

    @Mock
    private HttpHandler wrappedHandler;

    @Mock
    private AuthService authService;

    @Mock
    private HttpExchange httpExchange;

    private AuthMiddleware authMiddleware;
    private Headers requestHeaders;
    private Headers responseHeaders;
    private ByteArrayOutputStream responseBody;

    @BeforeEach
    void setUp() {
        authMiddleware = new AuthMiddleware(wrappedHandler, authService);
        requestHeaders = new Headers();
        responseHeaders = new Headers();
        responseBody = new ByteArrayOutputStream();

        lenient().when(httpExchange.getRequestHeaders()).thenReturn(requestHeaders);
        lenient().when(httpExchange.getResponseHeaders()).thenReturn(responseHeaders);
        lenient().when(httpExchange.getResponseBody()).thenReturn(responseBody);
        lenient().when(httpExchange.getRequestURI()).thenReturn(URI.create("/api/protected"));
    }

    @Test
    void testHandle_ValidBearerToken() throws Exception {
        // Arrange
        String sessionId = "valid-session-id";
        User user = new User("google123", "user@example.com", "Test User", null);

        requestHeaders.add("Authorization", "Bearer " + sessionId);
        when(authService.validateSession(sessionId)).thenReturn(user);

        // Act
        authMiddleware.handle(httpExchange);

        // Assert
        verify(authService).validateSession(sessionId);
        verify(httpExchange).setAttribute("user", user);
        verify(httpExchange).setAttribute("sessionId", sessionId);
        verify(wrappedHandler).handle(httpExchange);
        verify(httpExchange, never()).sendResponseHeaders(eq(401), anyLong());
    }

    @Test
    void testHandle_ValidCookie() throws Exception {
        // Arrange
        String sessionId = "valid-session-id";
        User user = new User("google123", "user@example.com", "Test User", null);

        requestHeaders.add("Cookie", "sessionId=" + sessionId + "; other=value");
        when(authService.validateSession(sessionId)).thenReturn(user);

        // Act
        authMiddleware.handle(httpExchange);

        // Assert
        verify(authService).validateSession(sessionId);
        verify(httpExchange).setAttribute("user", user);
        verify(wrappedHandler).handle(httpExchange);
    }

    @Test
    void testHandle_MissingSessionToken() throws Exception {
        // Arrange - no Authorization header or Cookie

        // Act
        authMiddleware.handle(httpExchange);

        // Assert
        verify(authService, never()).validateSession(anyString());
        verify(wrappedHandler, never()).handle(any());
        verify(httpExchange).sendResponseHeaders(eq(401), anyLong());

        String response = responseBody.toString();
        assertTrue(response.contains("Missing session token"));
    }

    @Test
    void testHandle_InvalidSession() throws Exception {
        // Arrange
        String sessionId = "invalid-session-id";
        requestHeaders.add("Authorization", "Bearer " + sessionId);

        when(authService.validateSession(sessionId))
                .thenThrow(new AuthService.UnauthorizedException("Session not found"));

        // Act
        authMiddleware.handle(httpExchange);

        // Assert
        verify(authService).validateSession(sessionId);
        verify(wrappedHandler, never()).handle(any());
        verify(httpExchange).sendResponseHeaders(eq(401), anyLong());

        String response = responseBody.toString();
        assertTrue(response.contains("Session not found"));
    }

    @Test
    void testHandle_ExpiredSession() throws Exception {
        // Arrange
        String sessionId = "expired-session-id";
        requestHeaders.add("Authorization", "Bearer " + sessionId);

        when(authService.validateSession(sessionId))
                .thenThrow(new AuthService.UnauthorizedException("Session expired"));

        // Act
        authMiddleware.handle(httpExchange);

        // Assert
        verify(httpExchange).sendResponseHeaders(eq(401), anyLong());

        String response = responseBody.toString();
        assertTrue(response.contains("Session expired"));
    }

    @Test
    void testHandle_InternalError() throws Exception {
        // Arrange
        String sessionId = "valid-session-id";
        requestHeaders.add("Authorization", "Bearer " + sessionId);

        when(authService.validateSession(sessionId))
                .thenThrow(new RuntimeException("Database error"));

        // Act
        authMiddleware.handle(httpExchange);

        // Assert
        verify(httpExchange).sendResponseHeaders(eq(500), anyLong());

        String response = responseBody.toString();
        assertTrue(response.contains("Internal server error"));
    }

    @Test
    void testExtractSessionId_PrefersBearerToken() throws Exception {
        // Arrange
        String bearerToken = "bearer-session";
        String cookieToken = "cookie-session";
        User user = new User("google123", "user@example.com", "Test User", null);

        requestHeaders.add("Authorization", "Bearer " + bearerToken);
        requestHeaders.add("Cookie", "sessionId=" + cookieToken);

        when(authService.validateSession(bearerToken)).thenReturn(user);

        // Act
        authMiddleware.handle(httpExchange);

        // Assert
        verify(authService).validateSession(bearerToken);
        verify(authService, never()).validateSession(cookieToken);
    }
}