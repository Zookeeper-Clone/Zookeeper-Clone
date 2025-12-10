package webserver.zookeeper.zookeeper_webserver.controllers;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import webserver.zookeeper.zookeeper_webserver.dto.auth.AuthResult;
import webserver.zookeeper.zookeeper_webserver.dto.auth.LoginRequestDTO;
import webserver.zookeeper.zookeeper_webserver.dto.auth.RegisterRequestDTO;
import webserver.zookeeper.zookeeper_webserver.services.AuthService;

public class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
    }

    @Test
    void testRegisterSuccess() throws Exception {
        RegisterRequestDTO dto = new RegisterRequestDTO("test@example.com", "Password123");
        AuthResult result = new AuthResult(true, "Success", "token123");

        when(authService.register(dto.email(), dto.password())).thenReturn(result);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().value("SESSION_TOKEN", "token123"))
                .andExpect(content().string("Registered successfully"));
    }

    @Test
    void testRegisterFailure() throws Exception {
        RegisterRequestDTO dto = new RegisterRequestDTO("test@example.com", "Password123");
        AuthResult result = new AuthResult(false, "Email exists", null);

        when(authService.register(dto.email(), dto.password())).thenReturn(result);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"password\":\"Password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Email exists"));
    }

    @Test
    void testLoginSuccess() throws Exception {
        LoginRequestDTO dto = new LoginRequestDTO("test@example.com", "Password123");
        AuthResult result = new AuthResult(true, "Success", "token456");

        when(authService.login(dto.email(), dto.password())).thenReturn(result);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().value("SESSION_TOKEN", "token456"))
                .andExpect(content().string("Logged in successfully"));
    }

    @Test
    void testLoginFailure() throws Exception {
        LoginRequestDTO dto = new LoginRequestDTO("test@example.com", "Password123");
        AuthResult result = new AuthResult(false, "Invalid credentials", null);

        when(authService.login(dto.email(), dto.password())).thenReturn(result);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"password\":\"Password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid credentials"));
    }

    @Test
    void testGetToken() throws Exception {
        mockMvc.perform(get("/auth/me")
                        .cookie(new Cookie("SESSION_TOKEN", "token789")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token789"));
    }

    @Test
    void testGoogleLoginRedirect() throws Exception {
        mockMvc.perform(get("/auth/google/login"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth2/authorization/google"));
    }
}
