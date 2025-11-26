package server.zookeeper.modules;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.ratis.protocol.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import server.zookeeper.DB.AuthRepository;
import server.zookeeper.proto.auth.*;
import server.zookeeper.util.PasswordHasher;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthHandler Tests")
class AuthHandlerTest {

    @Mock
    private AuthRepository authRepository;
    @Mock
    private PasswordHasher passwordHasher;

    private AuthHandler authHandler;

    private static final String VALID_EMAIL = "test@example.com";
    private static final String VALID_PASSWORD = "password123";
    private static final String HASHED_PASSWORD = "$2a$12$hashed_password";

    @BeforeEach
    void setUp() {
        authHandler = new AuthHandler(authRepository, passwordHasher);
    }

    // ========================================================================
    // Registration Tests
    // ========================================================================

    @Test
    @DisplayName("Should successfully register new user")
    void shouldSuccessfullyRegisterNewUser() throws InvalidProtocolBufferException {
        when(authRepository.userExists(VALID_EMAIL)).thenReturn(false);
        when(passwordHasher.hashPassword(VALID_PASSWORD)).thenReturn(HASHED_PASSWORD);

        AuthRequest request = AuthRequest.newBuilder()
                .setOperation(AuthOperationType.REGISTER)
                .setEmail(VALID_EMAIL)
                .setPassword(VALID_PASSWORD)
                .build();

        Message response = authHandler.handle(request.toByteArray(), true);
        AuthResponse authResponse = AuthResponse.parseFrom(response.getContent().toByteArray());

        assertTrue(authResponse.getSuccess());
        assertTrue(authResponse.getErrorMessage().isEmpty());
        assertEquals(VALID_EMAIL, authResponse.getUserInfo().getEmail());
        assertFalse(authResponse.getUserInfo().getIsAdmin());
        assertFalse(authResponse.getUserInfo().getCanCreateDirectories());

        verify(authRepository).userExists(VALID_EMAIL);
        verify(passwordHasher).hashPassword(VALID_PASSWORD);
        verify(authRepository).saveUser(any(UserAuth.class));
    }

    @Test
    @DisplayName("Should fail registration when user already exists")
    void shouldFailRegistrationWhenUserAlreadyExists() throws InvalidProtocolBufferException {
        when(authRepository.userExists(VALID_EMAIL)).thenReturn(true);

        AuthRequest request = AuthRequest.newBuilder()
                .setOperation(AuthOperationType.REGISTER)
                .setEmail(VALID_EMAIL)
                .setPassword(VALID_PASSWORD)
                .build();

        Message response = authHandler.handle(request.toByteArray(), true);
        AuthResponse authResponse = AuthResponse.parseFrom(response.getContent().toByteArray());

        assertFalse(authResponse.getSuccess());
        assertTrue(authResponse.getErrorMessage().contains("already exists"));

        verify(authRepository).userExists(VALID_EMAIL);
        verify(passwordHasher, never()).hashPassword(anyString());
        verify(authRepository, never()).saveUser(any());
    }

    @Test
    @DisplayName("Should fail registration with invalid email")
    void shouldFailRegistrationWithInvalidEmail() throws InvalidProtocolBufferException {
        AuthRequest request = AuthRequest.newBuilder()
                .setOperation(AuthOperationType.REGISTER)
                .setEmail("invalid-email")
                .setPassword(VALID_PASSWORD)
                .build();

        Message response = authHandler.handle(request.toByteArray(), true);
        AuthResponse authResponse = AuthResponse.parseFrom(response.getContent().toByteArray());

        assertFalse(authResponse.getSuccess());
        assertTrue(authResponse.getErrorMessage().contains("email"));

        verify(authRepository, never()).userExists(anyString());
        verify(authRepository, never()).saveUser(any());
    }

    @Test
    @DisplayName("Should fail registration with short password")
    void shouldFailRegistrationWithShortPassword() throws InvalidProtocolBufferException {
        AuthRequest request = AuthRequest.newBuilder()
                .setOperation(AuthOperationType.REGISTER)
                .setEmail(VALID_EMAIL)
                .setPassword("pass1")
                .build();

        Message response = authHandler.handle(request.toByteArray(), true);
        AuthResponse authResponse = AuthResponse.parseFrom(response.getContent().toByteArray());

        assertFalse(authResponse.getSuccess());
        assertTrue(authResponse.getErrorMessage().contains("at least"));

        verify(authRepository, never()).saveUser(any());
    }

    @Test
    @DisplayName("Should fail registration with password missing letters")
    void shouldFailRegistrationWithPasswordMissingLetters() throws InvalidProtocolBufferException {
        AuthRequest request = AuthRequest.newBuilder()
                .setOperation(AuthOperationType.REGISTER)
                .setEmail(VALID_EMAIL)
                .setPassword("12345678")
                .build();

        Message response = authHandler.handle(request.toByteArray(), true);
        AuthResponse authResponse = AuthResponse.parseFrom(response.getContent().toByteArray());

        assertFalse(authResponse.getSuccess());
        assertTrue(authResponse.getErrorMessage().contains("letters and numbers"));
    }

    @Test
    @DisplayName("Should fail registration with password missing digits")
    void shouldFailRegistrationWithPasswordMissingDigits() throws InvalidProtocolBufferException {
        AuthRequest request = AuthRequest.newBuilder()
                .setOperation(AuthOperationType.REGISTER)
                .setEmail(VALID_EMAIL)
                .setPassword("abcdefgh")
                .build();

        Message response = authHandler.handle(request.toByteArray(), true);
        AuthResponse authResponse = AuthResponse.parseFrom(response.getContent().toByteArray());

        assertFalse(authResponse.getSuccess());
        assertTrue(authResponse.getErrorMessage().contains("letters and numbers"));
    }

    // ========================================================================
    // Login Tests
    // ========================================================================

    @Test
    @DisplayName("Should successfully login with valid credentials")
    void shouldSuccessfullyLoginWithValidCredentials() throws InvalidProtocolBufferException {
        UserAuth userAuth = UserAuth.newBuilder()
                .setEmail(VALID_EMAIL)
                .setPasswordHash(HASHED_PASSWORD)
                .setIsAdmin(false)
                .setCanCreateDirectories(true)
                .build();

        when(authRepository.getUserByEmail(VALID_EMAIL)).thenReturn(Optional.of(userAuth));
        when(passwordHasher.verifyPassword(VALID_PASSWORD, HASHED_PASSWORD)).thenReturn(true);

        AuthRequest request = AuthRequest.newBuilder()
                .setOperation(AuthOperationType.LOGIN)
                .setEmail(VALID_EMAIL)
                .setPassword(VALID_PASSWORD)
                .build();

        Message response = authHandler.handle(request.toByteArray(), false);
        AuthResponse authResponse = AuthResponse.parseFrom(response.getContent().toByteArray());

        assertTrue(authResponse.getSuccess());
        assertTrue(authResponse.getErrorMessage().isEmpty());
        assertFalse(authResponse.getSessionToken().isEmpty());
        assertEquals(VALID_EMAIL, authResponse.getUserInfo().getEmail());

        verify(authRepository).getUserByEmail(VALID_EMAIL);
        verify(passwordHasher).verifyPassword(VALID_PASSWORD, HASHED_PASSWORD);
    }

    @Test
    @DisplayName("Should fail login with non-existent user")
    void shouldFailLoginWithNonExistentUser() throws InvalidProtocolBufferException {
        when(authRepository.getUserByEmail(VALID_EMAIL)).thenReturn(Optional.empty());

        AuthRequest request = AuthRequest.newBuilder()
                .setOperation(AuthOperationType.LOGIN)
                .setEmail(VALID_EMAIL)
                .setPassword(VALID_PASSWORD)
                .build();

        Message response = authHandler.handle(request.toByteArray(), false);
        AuthResponse authResponse = AuthResponse.parseFrom(response.getContent().toByteArray());

        assertFalse(authResponse.getSuccess());
        assertEquals("Invalid email or password", authResponse.getErrorMessage());

        verify(authRepository).getUserByEmail(VALID_EMAIL);
        verify(passwordHasher, never()).verifyPassword(anyString(), anyString());
    }

    @Test
    @DisplayName("Should fail login with incorrect password")
    void shouldFailLoginWithIncorrectPassword() throws InvalidProtocolBufferException {
        UserAuth userAuth = UserAuth.newBuilder()
                .setEmail(VALID_EMAIL)
                .setPasswordHash(HASHED_PASSWORD)
                .build();

        when(authRepository.getUserByEmail(VALID_EMAIL)).thenReturn(Optional.of(userAuth));
        when(passwordHasher.verifyPassword(VALID_PASSWORD, HASHED_PASSWORD)).thenReturn(false);

        AuthRequest request = AuthRequest.newBuilder()
                .setOperation(AuthOperationType.LOGIN)
                .setEmail(VALID_EMAIL)
                .setPassword(VALID_PASSWORD)
                .build();

        Message response = authHandler.handle(request.toByteArray(), false);
        AuthResponse authResponse = AuthResponse.parseFrom(response.getContent().toByteArray());

        assertFalse(authResponse.getSuccess());
        assertEquals("Invalid email or password", authResponse.getErrorMessage());

        verify(passwordHasher).verifyPassword(VALID_PASSWORD, HASHED_PASSWORD);
    }
}