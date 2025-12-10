package webserver.zookeeper.zookeeper_webserver.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import webserver.zookeeper.zookeeper_webserver.services.AuthService;
import webserver.zookeeper.zookeeper_webserver.dto.auth.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

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

    @PostMapping("/oauth/register")
    public ResponseEntity<?> registerOAuth(@RequestBody RegisterOAuthRequestDTO dto) {
        var result = auth.registerOAuth(dto.email(), dto.token());

        if (!result.success()) {
            return ResponseEntity.badRequest().body(result.message());
        }

        return ResponseEntity.ok("Registered via OAuth");
    }

    @PostMapping("/oauth/login")
    public ResponseEntity<?> loginOAuth(@RequestBody LoginOAuthRequestDTO dto) {
        var result = auth.loginOAuth(dto.email(), dto.token());

        if (!result.success()) {
            return ResponseEntity.badRequest().body(result.message());
        }

        return ResponseEntity.ok(
                new Object() {
                    public final String token = result.sessionToken();
                }
        );
    }
}
