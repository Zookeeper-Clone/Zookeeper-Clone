package webserver.zookeeper.zookeeper_webserver.dto.auth;

// Webserver-friendly version of AuthenticationResult to avoid leaking backend internals out of the web layer
public record AuthResult(boolean success, String message, String sessionToken) {}