package webserver.zookeeper.zookeeper_webserver.controllers;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import webserver.zookeeper.zookeeper_webserver.dto.auth.LoginRequestDTO;
import webserver.zookeeper.zookeeper_webserver.dto.auth.RegisterRequestDTO;
import webserver.zookeeper.zookeeper_webserver.services.AuthService;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService auth;


    @PostMapping("/register")
public ResponseEntity<?> register(@RequestBody RegisterRequestDTO dto, HttpServletResponse response) {
    var result = auth.register(dto.email(), dto.password());

    if (!result.success()) {
        return ResponseEntity.badRequest().body(result.message());
    }

    // Set session token as HTTP-only cookie
    Cookie cookie = new Cookie("SESSION_TOKEN", result.sessionToken());
    cookie.setHttpOnly(true);
    cookie.setPath("/");
    cookie.setMaxAge(60 * 60); // 1 hour
    response.addCookie(cookie);

    return ResponseEntity.ok("Registered successfully");
}

@PostMapping("/login")
public ResponseEntity<?> login(@RequestBody LoginRequestDTO dto, HttpServletResponse response) {
    var result = auth.login(dto.email(), dto.password());

    if (!result.success()) {
        return ResponseEntity.badRequest().body(result.message());
    }

    // Set session token as HTTP-only cookie
    Cookie cookie = new Cookie("SESSION_TOKEN", result.sessionToken());
    cookie.setHttpOnly(true);
    cookie.setPath("/");
    cookie.setMaxAge(60 * 60); // 1 hour
    response.addCookie(cookie);

    return ResponseEntity.ok("Logged in successfully");
}
    // LOGIN FLOW
    @GetMapping("/google/login")
    public void googleLogin(HttpServletResponse response) throws IOException {
        System.out.println("redirected user");
        response.sendRedirect("/oauth2/authorization/google");
    }

}
