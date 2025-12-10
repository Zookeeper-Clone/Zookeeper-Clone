package webserver.zookeeper.zookeeper_webserver.controllers;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import webserver.zookeeper.zookeeper_webserver.services.AuthService;
import webserver.zookeeper.zookeeper_webserver.dto.auth.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private final AuthService auth;


    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequestDTO dto) {
        var result = auth.register(dto.email(), dto.password());

        if (!result.success()) {
            return ResponseEntity.badRequest().body(result.message());
        }

        return ResponseEntity.ok("Registered successfully");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO dto) {
        var result = auth.login(dto.email(), dto.password());

        if (!result.success()) {
            return ResponseEntity.badRequest().body(result.message());
        }

        return ResponseEntity.ok(
                new Object() {
                    public final String token = result.sessionToken();
                }
        );
    }
    // LOGIN FLOW
    @GetMapping("/google/login")
    public void googleLogin(HttpServletResponse response) throws IOException {
        System.out.println("redirected user");
        response.sendRedirect("/oauth2/authorization/google");
    }

}
