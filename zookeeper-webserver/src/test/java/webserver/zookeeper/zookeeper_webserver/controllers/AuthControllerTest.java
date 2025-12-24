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
import webserver.zookeeper.zookeeper_webserver.services.ZookeeperService;

public class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @Mock
    private ZookeeperService zookeeperService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
    }

    @Test
    void testRegisterSuccess() throws Exception {
        AuthResult result = new AuthResult(true, "Success", "token123");
        when(authService.register(anyString(), anyString())).thenReturn(result);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().value("SESSION_TOKEN", "token123"))
                .andExpect(content().string("Registered successfully"));

        verify(zookeeperService).setToken("token123");
    }

    @Test
    void testRegisterFailure() throws Exception {
        AuthResult result = new AuthResult(false, "Email exists", null);
        when(authService.register(anyString(), anyString())).thenReturn(result);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"password\":\"Password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Email exists"));

        verify(zookeeperService, never()).setToken(any());
    }

    @Test
    void testLoginSuccess() throws Exception {
        AuthResult result = new AuthResult(true, "Success", "token456");
        when(authService.login(anyString(), anyString())).thenReturn(result);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().value("SESSION_TOKEN", "token456"))
                .andExpect(content().string("Logged in successfully"));

        verify(zookeeperService).setToken("token456");
    }

    @Test
    void testLoginFailure() throws Exception {
        AuthResult result = new AuthResult(false, "Invalid credentials", null);
        when(authService.login(anyString(), anyString())).thenReturn(result);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@example.com\",\"password\":\"Password123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid credentials"));

        verify(zookeeperService, never()).setToken(any());
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

    @Test
    void testLogoutSuccess() throws Exception {
        AuthResult result = new AuthResult(true, "Logged out successfully", null);
        when(authService.logout()).thenReturn(result);

        mockMvc.perform(post("/auth/logout")
                        .cookie(new Cookie("SESSION_TOKEN", "token123")))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("SESSION_TOKEN", 0))
                .andExpect(content().string("Logged out successfully"));

        verify(authService).logout();
        verify(zookeeperService).setToken(null);
    }

    @Test
    void testLogoutWithoutToken() throws Exception {
        AuthResult result = new AuthResult(true, "Logged out successfully", null);
        when(authService.logout()).thenReturn(result);

        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("SESSION_TOKEN", 0))
                .andExpect(content().string("Logged out successfully"));

        verify(authService).logout();
        verify(zookeeperService).setToken(null);
    }

    @Test
    void testLogoutFailure() throws Exception {
        AuthResult result = new AuthResult(false, "No active session to logout", null);
        when(authService.logout()).thenReturn(result);

        mockMvc.perform(post("/auth/logout")
                        .cookie(new Cookie("SESSION_TOKEN", "invalid_token")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("No active session to logout"));

        verify(authService).logout();
        verify(zookeeperService).setToken(null);
    }
}
