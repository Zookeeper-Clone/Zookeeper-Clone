package server.zookeeper.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Authentication middleware that wraps HTTP handlers to enforce authentication.
 *
 * Responsibilities:
 * - Intercepts incoming requests to protected endpoints
 * - Extracts session ID from Authorization header (Bearer token) or Cookie
 * - Validates session via AuthService.validateSession()
 * - Injects authenticated User and sessionId into HttpExchange attributes
 * - Forwards request to wrapped handler if authentication succeeds
 * - Returns 401 Unauthorized if authentication fails
 *
 * Session extraction priority:
 * 1. Authorization header: "Bearer <sessionId>"
 * 2. Cookie header: "sessionId=<sessionId>"
 *
 * On success:
 * - Sets exchange.getAttribute("user") to User object
 * - Sets exchange.getAttribute("sessionId") to session ID string
 * - Calls wrapped handler
 *
 * On failure:
 * - Returns 401 with JSON error message
 * - Does not call wrapped handler
 */
public class AuthMiddleware implements HttpHandler {
    private final HttpHandler handler;
    private final AuthService authService;

    public AuthMiddleware(HttpHandler handler, AuthService authService) {
        this.handler = handler;
        this.authService = authService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            // Extract session ID from Authorization header or Cookie
            String sessionId = extractSessionId(exchange);

            if (sessionId == null) {
                sendUnauthorized(exchange, "Missing session token");
                return;
            }

            // Validate session
            User user = authService.validateSession(sessionId);

            // Store user in exchange attributes for downstream handlers
            exchange.setAttribute("user", user);
            exchange.setAttribute("sessionId", sessionId);

            // Call the wrapped handler
            handler.handle(exchange);

        } catch (AuthService.UnauthorizedException e) {
            sendUnauthorized(exchange, e.getMessage());
        } catch (Exception e) {
            sendInternalError(exchange, e.getMessage());
        }
    }

    private String extractSessionId(HttpExchange exchange) {
        // Try Authorization header first (Bearer token)
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        // Try Cookie header
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader != null) {
            String[] cookies = cookieHeader.split(";");
            for (String cookie : cookies) {
                String[] parts = cookie.trim().split("=", 2);
                if (parts.length == 2 && "sessionId".equals(parts[0])) {
                    return parts[1];
                }
            }
        }

        return null;
    }

    private void sendUnauthorized(HttpExchange exchange, String message) throws IOException {
        String response = "{\"error\": \"" + message + "\"}";
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(401, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    private void sendInternalError(HttpExchange exchange, String message) throws IOException {
        String response = "{\"error\": \"Internal server error: " + message + "\"}";
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(500, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }
}