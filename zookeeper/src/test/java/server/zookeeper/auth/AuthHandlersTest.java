package server.zookeeper.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;  // ← Add this
import org.mockito.Mock;  // ← Add this
import org.mockito.junit.jupiter.MockitoExtension;  // ← Add this

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.Headers;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

@ExtendWith(MockitoExtension.class)  // ← Add this annotation
public class AuthHandlersTest {

    @Mock  // ← Add @Mock annotation
    private UserRepository userRepository;

    @Mock  // ← Add @Mock annotation
    private SessionRepository sessionRepository;

    @Mock  // ← Add @Mock annotation
    private GoogleOAuthClient googleOAuthClient;

    private AuthService authService;

    @BeforeEach
    public void setup() {
        // Mocks are now automatically initialized by MockitoExtension
        authService = new AuthService(userRepository, sessionRepository, googleOAuthClient);
    }

    // ===== LoginHandler Tests =====

    @Test
    public void testLoginHandler_NewUser() throws Exception {
        // Mock Google verification
        GoogleOAuthClient.GoogleUserInfo googleInfo = new GoogleOAuthClient.GoogleUserInfo(
                "google-123", "newuser@example.com", "New User", "https://pic.url"
        );
        when(googleOAuthClient.verifyIdToken("valid-token")).thenReturn(googleInfo);

        // User doesn't exist (sign up)
        when(userRepository.findByGoogleId("google-123")).thenReturn(null);

        // Mock saving
        doNothing().when(userRepository).save(any(User.class));
        doNothing().when(sessionRepository).save(any(Session.class));

        // Create handler
        LoginHandler handler = new LoginHandler(authService);

        // Mock HTTP request
        HttpExchange exchange = mock(HttpExchange.class);
        when(exchange.getRequestMethod()).thenReturn("POST");

        String requestBody = "{\"idToken\": \"valid-token\"}";
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(requestBody.getBytes()));

        Headers responseHeaders = new Headers();
        when(exchange.getResponseHeaders()).thenReturn(responseHeaders);

        ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        when(exchange.getResponseBody()).thenReturn(responseBody);

        // Handle request
        handler.handle(exchange);

        // Verify response
        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        String response = responseBody.toString();
        assertTrue(response.contains("sessionId"));
        assertTrue(response.contains("newuser@example.com"));

        // Verify user was created
        verify(userRepository, times(1)).save(any(User.class));
        verify(sessionRepository, times(1)).save(any(Session.class));
    }

    @Test
    public void testLoginHandler_ExistingUser() throws Exception {
        // Mock Google verification
        GoogleOAuthClient.GoogleUserInfo googleInfo = new GoogleOAuthClient.GoogleUserInfo(
                "google-456", "existing@example.com", "Existing User", "https://pic.url"
        );
        when(googleOAuthClient.verifyIdToken("valid-token")).thenReturn(googleInfo);

        // User exists (sign in)
        User existingUser = new User("google-456", "existing@example.com", "Existing User", "https://pic.url");
        when(userRepository.findByGoogleId("google-456")).thenReturn(existingUser);

        doNothing().when(userRepository).save(any(User.class));
        doNothing().when(sessionRepository).save(any(Session.class));

        // Create handler
        LoginHandler handler = new LoginHandler(authService);

        // Mock HTTP request
        HttpExchange exchange = mock(HttpExchange.class);
        when(exchange.getRequestMethod()).thenReturn("POST");

        String requestBody = "{\"idToken\": \"valid-token\"}";
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(requestBody.getBytes()));

        Headers responseHeaders = new Headers();
        when(exchange.getResponseHeaders()).thenReturn(responseHeaders);

        ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        when(exchange.getResponseBody()).thenReturn(responseBody);

        // Handle request
        handler.handle(exchange);

        // Verify response
        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        String response = responseBody.toString();
        assertTrue(response.contains("sessionId"));
        assertTrue(response.contains("existing@example.com"));

        // Verify last login was updated
        verify(userRepository, times(1)).save(existingUser);
    }

    @Test
    public void testLoginHandler_InvalidToken() throws Exception {
        // Mock invalid token
        when(googleOAuthClient.verifyIdToken("invalid-token"))
                .thenThrow(new IllegalArgumentException("Invalid ID token"));

        LoginHandler handler = new LoginHandler(authService);

        // Mock HTTP request
        HttpExchange exchange = mock(HttpExchange.class);
        when(exchange.getRequestMethod()).thenReturn("POST");

        String requestBody = "{\"idToken\": \"invalid-token\"}";
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(requestBody.getBytes()));

        Headers responseHeaders = new Headers();
        when(exchange.getResponseHeaders()).thenReturn(responseHeaders);

        ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        when(exchange.getResponseBody()).thenReturn(responseBody);

        // Handle request
        handler.handle(exchange);

        // Verify 401 response
        verify(exchange).sendResponseHeaders(eq(401), anyLong());
        String response = responseBody.toString();
        assertTrue(response.contains("Authentication failed"));
    }

    @Test
    public void testLoginHandler_MissingIdToken() throws Exception {
        LoginHandler handler = new LoginHandler(authService);

        // Mock HTTP request with empty JSON (no idToken field)
        HttpExchange exchange = mock(HttpExchange.class);
        when(exchange.getRequestMethod()).thenReturn("POST");

        String requestBody = "{}";
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(requestBody.getBytes()));

        Headers responseHeaders = new Headers();
        when(exchange.getResponseHeaders()).thenReturn(responseHeaders);

        ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        when(exchange.getResponseBody()).thenReturn(responseBody);

        // Handle request
        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(anyInt(), anyLong());
        String response = responseBody.toString();
        assertTrue(response.contains("error"));
    }

    @Test
    public void testLoginHandler_WrongMethod() throws Exception {
        LoginHandler handler = new LoginHandler(authService);

        // Mock HTTP GET request (should be POST)
        HttpExchange exchange = mock(HttpExchange.class);
        when(exchange.getRequestMethod()).thenReturn("GET");

        Headers responseHeaders = new Headers();
        when(exchange.getResponseHeaders()).thenReturn(responseHeaders);

        ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        when(exchange.getResponseBody()).thenReturn(responseBody);

        // Handle request
        handler.handle(exchange);

        // Verify 405 response
        verify(exchange).sendResponseHeaders(eq(405), anyLong());
        String response = responseBody.toString();
        assertTrue(response.contains("Method not allowed"));
    }

    // ===== LogoutHandler Tests =====

    @Test
    public void testLogoutHandler_Success() throws Exception {
        // Mock the delete to do nothing (it returns void)
        doNothing().when(sessionRepository).delete(anyString());

        LogoutHandler handler = new LogoutHandler(authService);

        // Mock HTTP request
        HttpExchange exchange = mock(HttpExchange.class);
        when(exchange.getRequestMethod()).thenReturn("POST");
        when(exchange.getAttribute("sessionId")).thenReturn("session-123");

        Headers responseHeaders = new Headers();
        when(exchange.getResponseHeaders()).thenReturn(responseHeaders);

        ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        when(exchange.getResponseBody()).thenReturn(responseBody);

        // Handle request
        handler.handle(exchange);

        // Verify response
        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        String response = responseBody.toString();
        assertTrue(response.contains("Signed out successfully"));

        // Verify delete was called
        verify(sessionRepository).delete("session-123");
    }

    @Test
    public void testLogoutHandler_NoSession() throws Exception {
        LogoutHandler handler = new LogoutHandler(authService);

        // Mock HTTP request without session
        HttpExchange exchange = mock(HttpExchange.class);
        when(exchange.getRequestMethod()).thenReturn("POST");
        when(exchange.getAttribute("sessionId")).thenReturn(null);

        Headers responseHeaders = new Headers();
        when(exchange.getResponseHeaders()).thenReturn(responseHeaders);

        ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        when(exchange.getResponseBody()).thenReturn(responseBody);

        // Handle request
        handler.handle(exchange);

        // Verify 401 response
        verify(exchange).sendResponseHeaders(eq(401), anyLong());
    }

    // ===== ProfileHandler Tests =====

    @Test
    public void testProfileHandler_Success() throws Exception {
        User user = new User("google-789", "profile@example.com", "Profile User", "https://pic.url");

        ProfileHandler handler = new ProfileHandler();

        // Mock HTTP request
        HttpExchange exchange = mock(HttpExchange.class);
        when(exchange.getRequestMethod()).thenReturn("GET");
        when(exchange.getAttribute("user")).thenReturn(user);

        Headers responseHeaders = new Headers();
        when(exchange.getResponseHeaders()).thenReturn(responseHeaders);

        ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        when(exchange.getResponseBody()).thenReturn(responseBody);

        // Handle request
        handler.handle(exchange);

        // Verify response
        verify(exchange).sendResponseHeaders(eq(200), anyLong());
        String response = responseBody.toString();
        assertTrue(response.contains("profile@example.com"));
        assertTrue(response.contains("Profile User"));
    }

    @Test
    public void testProfileHandler_NoUser() throws Exception {
        ProfileHandler handler = new ProfileHandler();

        // Mock HTTP request without user
        HttpExchange exchange = mock(HttpExchange.class);
        when(exchange.getRequestMethod()).thenReturn("GET");
        when(exchange.getAttribute("user")).thenReturn(null);

        Headers responseHeaders = new Headers();
        when(exchange.getResponseHeaders()).thenReturn(responseHeaders);

        ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        when(exchange.getResponseBody()).thenReturn(responseBody);

        // Handle request
        handler.handle(exchange);

        // Verify 401 response
        verify(exchange).sendResponseHeaders(eq(401), anyLong());
    }
}