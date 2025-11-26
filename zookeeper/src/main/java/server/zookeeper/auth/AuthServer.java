package server.zookeeper.auth;

import com.sun.net.httpserver.HttpServer;
import server.zookeeper.DB.CRocksDB;

import java.net.InetSocketAddress;

/**
 * Main server entry point that orchestrates the authentication system.
 *
 * Responsibilities:
 * - Initializes RocksDB database connection
 * - Creates repository instances (UserRepository, SessionRepository)
 * - Initializes GoogleOAuthClient with client ID from environment
 * - Creates AuthService with all dependencies
 * - Registers HTTP endpoints (public and protected routes)
 * - Starts HTTP server on port 8080
 *
 * Public endpoints (no authentication):
 * - POST /auth/google  - Google OAuth sign-in/sign-up
 * - GET  /health       - Health check
 *
 * Protected endpoints (require valid session):
 * - POST /auth/logout  - Sign out and delete session
 * - GET  /api/profile  - Get authenticated user profile
 */
public class AuthServer {

    public static void main(String[] args) throws Exception {
        // Set DB_PATH environment variable if not set
        if (System.getenv("DB_PATH") == null) {
            System.setProperty("DB_PATH", "./auth-data");
        }

        // Initialize database
        CRocksDB db = CRocksDB.getInstance();

        // Initialize repositories
        UserRepository userRepository = new UserRepository(db);
        SessionRepository sessionRepository = new SessionRepository(db);

        // Initialize Google OAuth client
        String googleClientId = System.getenv("GOOGLE_CLIENT_ID");
        if (googleClientId == null) {
            System.err.println("WARNING: GOOGLE_CLIENT_ID not set. Using test client.");
            googleClientId = "test-client-id";
        }
        GoogleOAuthClient googleOAuthClient = new GoogleOAuthClient(googleClientId);

        // Initialize auth service
        AuthService authService = new AuthService(
                userRepository,
                sessionRepository,
                googleOAuthClient
        );

        // Create HTTP server
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // PUBLIC endpoints (no authentication required)
        server.createContext("/auth/google", new LoginHandler(authService));

        // PROTECTED endpoints (require authentication)
        server.createContext("/auth/logout",
                new AuthMiddleware(new LogoutHandler(authService), authService));

        server.createContext("/api/profile",
                new AuthMiddleware(new ProfileHandler(), authService));

        // Health check endpoint
        server.createContext("/health", exchange -> {
            String response = "{\"status\": \"ok\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.getBytes().length);
            exchange.getResponseBody().write(response.getBytes());
            exchange.getResponseBody().close();
        });

        // Start server
        server.setExecutor(null);
        server.start();

        System.out.println("=========================================");
        System.out.println("Auth Server started on http://localhost:8080");
        System.out.println("=========================================");
        System.out.println("Endpoints:");
        System.out.println("  POST   /auth/google  - Sign in/up with Google");
        System.out.println("  POST   /auth/logout  - Sign out (requires auth)");
        System.out.println("  GET    /api/profile  - Get user profile (requires auth)");
        System.out.println("  GET    /health       - Health check");
        System.out.println("=========================================");
    }
}