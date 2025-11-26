package server.zookeeper.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Handles Google OAuth authentication for both sign-in and sign-up.
 *
 * Endpoint: POST /auth/google
 *
 * Request body:
 * {
 *   "idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6..." (Google ID token JWT)
 * }
 *
 * Success response (200):
 * {
 *   "user": {
 *     "id": "user-uuid",
 *     "email": "user@gmail.com",
 *     "name": "John Doe",
 *     ...
 *   },
 *   "sessionId": "session-uuid"
 * }
 *
 * Flow:
 * 1. Validates request method is POST
 * 2. Parses JSON body to extract idToken
 * 3. Calls AuthService.authenticateWithGoogle(idToken)
 * 4. Returns user object and new session ID
 *
 * This endpoint handles both new user registration and existing user login
 * transparently - the AuthService determines which action to take.
 */
public class LoginHandler implements HttpHandler {
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    public LoginHandler(AuthService authService) {
        this.authService = authService;
        this.objectMapper = new ObjectMapper();
        // Register JavaTimeModule to handle Instant serialization
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Only allow POST
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        try {
            // Read request body
            String requestBody = new String(exchange.getRequestBody().readAllBytes());

            // Parse JSON
            JsonNode json = objectMapper.readTree(requestBody);
            String idToken = json.get("idToken").asText();

            if (idToken == null || idToken.isEmpty()) {
                sendError(exchange, 400, "Missing idToken");
                return;
            }

            // Authenticate with Google (handles both sign up and sign in)
            AuthService.AuthResult result = authService.authenticateWithGoogle(idToken);

            // Build response
            String response = String.format(
                    "{\"user\": %s, \"sessionId\": \"%s\"}",
                    objectMapper.writeValueAsString(result.getUser()),
                    result.getSessionId()
            );

            // Send success response
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }

        } catch (Exception e) {
            sendError(exchange, 401, "Authentication failed: " + e.getMessage());
        }
    }

    private void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        String response = String.format("{\"error\": \"%s\"}", message);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }
}