package server.zookeeper.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Handles user logout by deleting the active session.
 *
 * Endpoint: POST /auth/logout
 *
 * Requirements:
 * - Must be wrapped with AuthMiddleware (requires valid session)
 * - Request must include valid session in Authorization header or Cookie
 *
 * Success response (200):
 * {
 *   "message": "Signed out successfully"
 * }
 *
 * Flow:
 * 1. AuthMiddleware validates session and injects sessionId into exchange
 * 2. Handler validates request method is POST
 * 3. Retrieves sessionId from exchange attributes
 * 4. Calls AuthService.signOut(sessionId) to delete session
 * 5. Returns success message
 *
 * After logout, the session is permanently deleted from the database
 * and cannot be used for further authenticated requests.
 */
public class LogoutHandler implements HttpHandler {
    private final AuthService authService;

    public LogoutHandler(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Only allow POST
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed");
            return;
        }

        try {
            // Get session ID from middleware (already validated)
            String sessionId = (String) exchange.getAttribute("sessionId");

            if (sessionId == null) {
                sendError(exchange, 401, "Unauthorized");
                return;
            }

            // Delete session
            authService.signOut(sessionId);

            // Send success response
            String response = "{\"message\": \"Signed out successfully\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }

        } catch (Exception e) {
            sendError(exchange, 500, "Logout failed: " + e.getMessage());
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