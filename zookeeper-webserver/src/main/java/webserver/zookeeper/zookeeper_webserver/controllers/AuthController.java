package webserver.zookeeper.zookeeper_webserver.controllers;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import webserver.zookeeper.zookeeper_webserver.dto.auth.AuthResult;
import webserver.zookeeper.zookeeper_webserver.dto.auth.LoginRequestDTO;
import webserver.zookeeper.zookeeper_webserver.dto.auth.RegisterRequestDTO;
import webserver.zookeeper.zookeeper_webserver.services.AuthService;
import webserver.zookeeper.zookeeper_webserver.services.ZookeeperService;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private ZookeeperService zookeeperService;
    @Autowired
    private AuthService auth;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequestDTO dto, HttpServletResponse response) {
        AuthResult result = auth.register(dto.email(), dto.password());
        if (!result.success()) return ResponseEntity.badRequest().body(result.message());
        setSessionCookie(response, result);
        zookeeperService.setToken(result.sessionToken());
        return ResponseEntity.ok("Registered successfully");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO dto, HttpServletResponse response) {
        AuthResult result = auth.login(dto.email(), dto.password());
        if (!result.success()) return ResponseEntity.badRequest().body(result.message());
        setSessionCookie(response, result);
        zookeeperService.setToken(result.sessionToken());
        return ResponseEntity.ok("Logged in successfully");
    }

    private void setSessionCookie(HttpServletResponse response, AuthResult result) {
        Cookie cookie = new Cookie("SESSION_TOKEN", result.sessionToken());
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60); // 1 hour
        response.addCookie(cookie);
    }

    @GetMapping("/google/login")
    public void googleLogin(HttpServletResponse response) throws IOException {
        response.sendRedirect("/oauth2/authorization/google");
    }

    @GetMapping("/me")
    public Map<String, String> getToken(@CookieValue("SESSION_TOKEN") String token) {
        return Map.of("token", token);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@CookieValue(value = "SESSION_TOKEN", required = false) String token, 
                                     HttpServletResponse response) {
        AuthResult result = auth.logout();
        zookeeperService.setToken(null);
        
        Cookie cookie = new Cookie("SESSION_TOKEN", "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        
        if (!result.success()) {
            return ResponseEntity.badRequest().body(result.message());
        }
        return ResponseEntity.ok("Logged out successfully");
    }
}
