package server.zookeeper.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Example protected endpoint that returns authenticated user's profile.
 *
 * Endpoint: GET /api/profile
 *
 * Requirements:
 * - Must be wrapped with AuthMiddleware (requires valid session)
 * - Request must include valid session in Authorization header or Cookie
 *
 * Success response (200):
 * {
 *   "id": "user-uuid",
 *   "googleId": "117234567890",
 *   "email": "user@gmail.com",
 *   "name": "John Doe",
 *   "pictureUrl": "https://...",
 *   "createdAt": "2025-11-26T10:00:00.000Z",
 *   "lastLoginAt": "2025-11-26T10:00:00.000Z"
 * }
 *
 * Flow:
 * 1. AuthMiddleware validates session and injects User object into exchange
 * 2. Handler validates request method is GET
 * 3. Retrieves User from exchange attributes
 * 4. Serializes User to JSON using Jackson ObjectMapper
 * 5. Returns user profile
 *
 * This serves as a reference implementation for building other protected endpoints.
 */
public class ProfileHandler implements HttpHandler {
    private final ObjectMapper objectMapper;

    public ProfileHandler() {
        this.objectMapper = new ObjectMapper();
        // Register JavaTimeModule to handle Instant serialization
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Only allow GET
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        try {
            // Get user from middleware (already validated)
            User user = (User) exchange.getAttribute("user");

            if (user == null) {
                sendError(exchange, 401, "Unauthorized");
                return;
            }

            // Return user profile
            String response = objectMapper.writeValueAsString(user);

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }

        } catch (Exception e) {
            sendError(exchange, 500, "Failed to get profile: " + e.getMessage());
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