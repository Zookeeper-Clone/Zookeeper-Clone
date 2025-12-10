package webserver.zookeeper.zookeeper_webserver.controllers;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import webserver.zookeeper.zookeeper_webserver.services.AuthService;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;
    // LOGIN FLOW
    @GetMapping("/google/login")
    public void googleLogin(HttpServletResponse response) throws IOException {
        System.out.println("redirected user");
        response.sendRedirect("/oauth2/authorization/google");
    }

}
