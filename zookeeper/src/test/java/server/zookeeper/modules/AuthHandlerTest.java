package server.zookeeper.modules;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.ratis.protocol.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import server.zookeeper.DB.AuthRepository;
import server.zookeeper.proto.auth.*;
import server.zookeeper.util.PasswordHasher;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;

import java.io.IOException;
import java.security.GeneralSecurityException;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthHandler Tests")
class AuthHandlerTest {

    @Mock
    private AuthRepository authRepository;
    @Mock
    private PasswordHasher passwordHasher;
    @Mock
    private GoogleIdTokenVerifier verifier;
    @Mock
    private SessionManager sessionManager;
    private AuthHandler authHandler;

    private static final String VALID_EMAIL = "test@example.com";
    private static final String CURRENT_PASSWORD = "oldPassword123";
    private static final String NEW_PASSWORD = "newPassword456";
    private static final String CURRENT_HASH = "$2a$12$current_hash";
    private static final String NEW_HASH = "$2a$12$new_hash";

    @BeforeEach
    void setUp() {
        authHandler = new AuthHandler(authRepository, passwordHasher, verifier, sessionManager);
    }

    // ========================================================================
    // Registration Tests
    // ========================================================================

    @Test
    @DisplayName("Should successfully register new user")
    void shouldSuccessfullyRegisterNewUser() throws InvalidProtocolBufferException {
        when(authRepository.userExists(VALID_EMAIL)).thenReturn(false);
        when(passwordHasher.hashPassword(CURRENT_PASSWORD)).thenReturn(CURRENT_HASH);

        AuthRequest request = AuthRequest.newBuilder()
                .setOperation(AuthOperationType.REGISTER)
                .setEmail(VALID_EMAIL)
                .setPassword(CURRENT_PASSWORD)
                .build();

        Message response = authHandler.handle(request.toByteArray(), true);
        AuthResponse authResponse = AuthResponse.parseFrom(response.getContent().toByteArray());

        assertTrue(authResponse.getSuccess());
        assertTrue(authResponse.getErrorMessage().isEmpty());
        assertEquals(VALID_EMAIL, authResponse.getUserInfo().getEmail());
        assertFalse(authResponse.getUserInfo().getIsAdmin());
        assertFalse(authResponse.getUserInfo().getCanCreateDirectories());

        verify(authRepository).userExists(VALID_EMAIL);
        verify(passwordHasher).hashPassword(CURRENT_PASSWORD);
        verify(authRepository).saveUser(any(UserAuth.class));
    }

    @Test
    @DisplayName("Should fail registration when user already exists")
    void shouldFailRegistrationWhenUserAlreadyExists() throws InvalidProtocolBufferException {
        when(authRepository.userExists(VALID_EMAIL)).thenReturn(true);

        AuthRequest request = AuthRequest.newBuilder()
                .setOperation(AuthOperationType.REGISTER)
                .setEmail(VALID_EMAIL)
                .setPassword(CURRENT_PASSWORD)
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
                .setPassword(CURRENT_PASSWORD)
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
                .setPasswordHash(CURRENT_HASH)
                .setIsAdmin(false)
                .setCanCreateDirectories(true)
                .build();

        when(authRepository.getUserByEmail(VALID_EMAIL)).thenReturn(Optional.of(userAuth));
        when(passwordHasher.verifyPassword(CURRENT_PASSWORD, CURRENT_HASH)).thenReturn(true);
        String expectedToken = "session-token-123";

        when(sessionManager.createSession(eq(VALID_EMAIL), eq(expectedToken))).thenReturn(expectedToken);

        AuthRequest request = AuthRequest.newBuilder()
                .setOperation(AuthOperationType.LOGIN)
                .setEmail(VALID_EMAIL)
                .setPassword(CURRENT_PASSWORD)
                .setSessionToken(expectedToken) // simulate leader injecting the token
                .build();

        Message response = authHandler.handle(request.toByteArray(), false);
        AuthResponse authResponse = AuthResponse.parseFrom(response.getContent().toByteArray());

        assertTrue(authResponse.getSuccess());
        assertTrue(authResponse.getErrorMessage().isEmpty());
        assertFalse(authResponse.getSessionToken().isEmpty());
        assertEquals(VALID_EMAIL, authResponse.getUserInfo().getEmail());
        assertEquals(expectedToken, authResponse.getSessionToken());

        verify(authRepository).getUserByEmail(VALID_EMAIL);
        verify(passwordHasher).verifyPassword(CURRENT_PASSWORD, CURRENT_HASH);
        verify(sessionManager).createSession(VALID_EMAIL, expectedToken);    }

    @Test
    @DisplayName("Should fail login with non-existent user")
    void shouldFailLoginWithNonExistentUser() throws InvalidProtocolBufferException {
        when(authRepository.getUserByEmail(VALID_EMAIL)).thenReturn(Optional.empty());

        AuthRequest request = AuthRequest.newBuilder()
                .setOperation(AuthOperationType.LOGIN)
                .setEmail(VALID_EMAIL)
                .setPassword(CURRENT_PASSWORD)
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
                .setPasswordHash(CURRENT_HASH)
                .build();

        when(authRepository.getUserByEmail(VALID_EMAIL)).thenReturn(Optional.of(userAuth));
        when(passwordHasher.verifyPassword(CURRENT_PASSWORD, CURRENT_HASH)).thenReturn(false);

        AuthRequest request = AuthRequest.newBuilder()
                .setOperation(AuthOperationType.LOGIN)
                .setEmail(VALID_EMAIL)
                .setPassword(CURRENT_PASSWORD)
                .build();

        Message response = authHandler.handle(request.toByteArray(), false);
        AuthResponse authResponse = AuthResponse.parseFrom(response.getContent().toByteArray());

        assertFalse(authResponse.getSuccess());
        assertEquals("Invalid email or password", authResponse.getErrorMessage());

        verify(passwordHasher).verifyPassword(CURRENT_PASSWORD, CURRENT_HASH);
    }

    @Test
    @DisplayName("Should successfully change password with valid credentials")
    void shouldSuccessfullyChangePassword() throws InvalidProtocolBufferException {
        UserAuth existingUser = UserAuth.newBuilder()
                .setEmail(VALID_EMAIL)
                .setPasswordHash(CURRENT_HASH)
                .setIsAdmin(false)
                .setCanCreateDirectories(true)
                .build();

        when(authRepository.getUserByEmail(VALID_EMAIL)).thenReturn(Optional.of(existingUser));
        when(passwordHasher.verifyPassword(CURRENT_PASSWORD, CURRENT_HASH)).thenReturn(true);
        when(passwordHasher.verifyPassword(NEW_PASSWORD, CURRENT_HASH)).thenReturn(false);
        when(passwordHasher.hashPassword(NEW_PASSWORD)).thenReturn(NEW_HASH);

        AuthRequest request = AuthRequest.newBuilder()
                .setOperation(AuthOperationType.CHANGE_PASSWORD)
                .setEmail(VALID_EMAIL)
                .setPassword(CURRENT_PASSWORD)
                .setNewPassword(NEW_PASSWORD)
                .build();

        Message response = authHandler.handle(request.toByteArray(), true);
        AuthResponse authResponse = AuthResponse.parseFrom(response.getContent().toByteArray());

        assertTrue(authResponse.getSuccess());
        assertTrue(authResponse.getErrorMessage().isEmpty());
        assertEquals(VALID_EMAIL, authResponse.getUserInfo().getEmail());

        ArgumentCaptor<UserAuth> userCaptor = ArgumentCaptor.forClass(UserAuth.class);
        verify(authRepository).updateUser(userCaptor.capture());

        UserAuth updatedUser = userCaptor.getValue();
        assertEquals(VALID_EMAIL, updatedUser.getEmail());
        assertEquals(NEW_HASH, updatedUser.getPasswordHash());
        assertEquals(existingUser.getIsAdmin(), updatedUser.getIsAdmin());
        assertEquals(existingUser.getCanCreateDirectories(), updatedUser.getCanCreateDirectories());
    }

    @Test
    @DisplayName("Should fail when user does not exist")
    void shouldFailWhenUserDoesNotExist() throws InvalidProtocolBufferException {
        when(authRepository.getUserByEmail(VALID_EMAIL)).thenReturn(Optional.empty());

        AuthRequest request = AuthRequest.newBuilder()
                .setOperation(AuthOperationType.CHANGE_PASSWORD)
                .setEmail(VALID_EMAIL)
                .setPassword(CURRENT_PASSWORD)
                .setNewPassword(NEW_PASSWORD)
                .build();

        Message response = authHandler.handle(request.toByteArray(), true);
        AuthResponse authResponse = AuthResponse.parseFrom(response.getContent().toByteArray());

        assertFalse(authResponse.getSuccess());
        assertEquals("Invalid credentials", authResponse.getErrorMessage());

        verify(authRepository, never()).updateUser(any());
    }

    @Test
    @DisplayName("Should fail when current password is incorrect")
    void shouldFailWhenCurrentPasswordIsIncorrect() throws InvalidProtocolBufferException {
        UserAuth existingUser = UserAuth.newBuilder()
                .setEmail(VALID_EMAIL)
                .setPasswordHash(CURRENT_HASH)
                .build();

        when(authRepository.getUserByEmail(VALID_EMAIL)).thenReturn(Optional.of(existingUser));
        when(passwordHasher.verifyPassword(CURRENT_PASSWORD, CURRENT_HASH)).thenReturn(false);

        AuthRequest request = AuthRequest.newBuilder()
                .setOperation(AuthOperationType.CHANGE_PASSWORD)
                .setEmail(VALID_EMAIL)
                .setPassword(CURRENT_PASSWORD)
                .setNewPassword(NEW_PASSWORD)
                .build();

        Message response = authHandler.handle(request.toByteArray(), true);
        AuthResponse authResponse = AuthResponse.parseFrom(response.getContent().toByteArray());

        assertFalse(authResponse.getSuccess());
        assertEquals("Invalid credentials", authResponse.getErrorMessage());

        verify(authRepository, never()).updateUser(any());
        verify(passwordHasher, never()).hashPassword(anyString());
    }

    @Test
    @DisplayName("Should fail when new password is same as current password")
    void shouldFailWhenNewPasswordIsSameAsCurrent() throws InvalidProtocolBufferException {
        UserAuth existingUser = UserAuth.newBuilder()
                .setEmail(VALID_EMAIL)
                .setPasswordHash(CURRENT_HASH)
                .build();

        when(authRepository.getUserByEmail(VALID_EMAIL)).thenReturn(Optional.of(existingUser));
        when(passwordHasher.verifyPassword(CURRENT_PASSWORD, CURRENT_HASH)).thenReturn(true);
        when(passwordHasher.verifyPassword(NEW_PASSWORD, CURRENT_HASH)).thenReturn(true);

        AuthRequest request = AuthRequest.newBuilder()
                .setOperation(AuthOperationType.CHANGE_PASSWORD)
                .setEmail(VALID_EMAIL)
                .setPassword(CURRENT_PASSWORD)
                .setNewPassword(NEW_PASSWORD)
                .build();

        Message response = authHandler.handle(request.toByteArray(), true);
        AuthResponse authResponse = AuthResponse.parseFrom(response.getContent().toByteArray());

        assertFalse(authResponse.getSuccess());
        assertTrue(authResponse.getErrorMessage().contains("must be different"));

        verify(authRepository, never()).updateUser(any());
    }
    // ========================================================================
    // OAuth Registration Tests
    // ========================================================================

    @Test
    @DisplayName("Should successfully register new user via OAuth")
    void shouldSuccessfullyRegisterOAuthUser() throws Exception {
        String tokenString = "valid-google-token";

        GoogleIdToken mockToken = mock(GoogleIdToken.class);
        Payload mockPayload = mock(Payload.class);
        when(verifier.verify(tokenString)).thenReturn(mockToken);
        when(mockToken.getPayload()).thenReturn(mockPayload);
        when(mockPayload.getEmail()).thenReturn(VALID_EMAIL);

        when(authRepository.userExists(VALID_EMAIL)).thenReturn(false);

        AuthRequest request = AuthRequest.newBuilder()
                .setOperation(AuthOperationType.REGISTER_OAUTH)
                .setEmail(VALID_EMAIL)
                .setGoogleToken(tokenString)
                .build();

        Message response = authHandler.handle(request.toByteArray(), true);
        AuthResponse authResponse = AuthResponse.parseFrom(response.getContent().toByteArray());

        assertTrue(authResponse.getSuccess());
        assertTrue(authResponse.getErrorMessage().isEmpty());
        assertEquals(VALID_EMAIL, authResponse.getUserInfo().getEmail());

        verify(authRepository).saveOAuthUser(any(UserAuth.class));
    }

    @Test
    @DisplayName("Should fail OAuth registration if email does not match token email")
    void shouldFailOAuthRegisterWhenEmailMismatch() throws Exception {
        String tokenString = "valid-google-token";
        String differentEmail = "other@example.com";

        GoogleIdToken mockToken = mock(GoogleIdToken.class);
        Payload mockPayload = mock(Payload.class);
        when(verifier.verify(tokenString)).thenReturn(mockToken);
        when(mockToken.getPayload()).thenReturn(mockPayload);
        when(mockPayload.getEmail()).thenReturn(differentEmail);

        AuthRequest request = AuthRequest.newBuilder()
                .setOperation(AuthOperationType.REGISTER_OAUTH)
                .setEmail(VALID_EMAIL)
                .setGoogleToken(tokenString)
                .build();

        Message response = authHandler.handle(request.toByteArray(), true);
        AuthResponse authResponse = AuthResponse.parseFrom(response.getContent().toByteArray());

        assertFalse(authResponse.getSuccess());
        assertTrue(authResponse.getErrorMessage().contains("doesn't match"));
        verify(authRepository, never()).saveOAuthUser(any());
    }

    @Test
    @DisplayName("Should fail OAuth registration if Google Token is invalid")
    void shouldFailOAuthRegisterWithInvalidToken() throws Exception {
        String invalidToken = "invalid-token";

        when(verifier.verify(invalidToken)).thenThrow(new GeneralSecurityException("Invalid Token"));

        AuthRequest request = AuthRequest.newBuilder()
                .setOperation(AuthOperationType.REGISTER_OAUTH)
                .setEmail(VALID_EMAIL)
                .setGoogleToken(invalidToken)
                .build();

        Message response = authHandler.handle(request.toByteArray(), true);
        AuthResponse authResponse = AuthResponse.parseFrom(response.getContent().toByteArray());

        assertFalse(authResponse.getSuccess());
        verify(authRepository, never()).saveOAuthUser(any());
    }

    @Test
    @DisplayName("Should fail OAuth registration if user already exists")
    void shouldFailOAuthRegisterIfUserExists() throws Exception {
        String tokenString = "valid-google-token";

        GoogleIdToken mockToken = mock(GoogleIdToken.class);
        Payload mockPayload = mock(Payload.class);
        when(verifier.verify(tokenString)).thenReturn(mockToken);
        when(mockToken.getPayload()).thenReturn(mockPayload);
        when(mockPayload.getEmail()).thenReturn(VALID_EMAIL);

        when(authRepository.userExists(VALID_EMAIL)).thenReturn(true);

        AuthRequest request = AuthRequest.newBuilder()
                .setOperation(AuthOperationType.REGISTER_OAUTH)
                .setEmail(VALID_EMAIL)
                .setGoogleToken(tokenString)
                .build();

        Message response = authHandler.handle(request.toByteArray(), true);
        AuthResponse authResponse = AuthResponse.parseFrom(response.getContent().toByteArray());

        assertFalse(authResponse.getSuccess());
        assertEquals("User with this email already exists", authResponse.getErrorMessage());
    }

    // ========================================================================
    // OAuth Login Tests
    // ========================================================================

    @Test
    @DisplayName("Should successfully login via OAuth")
    void shouldSuccessfullyLoginOAuth() throws Exception {
        String tokenString = "valid-google-token";
        String expectedToken = "new-session-token";

        // Mock Token
        GoogleIdToken mockToken = mock(GoogleIdToken.class);
        Payload mockPayload = mock(Payload.class);
        when(verifier.verify(tokenString)).thenReturn(mockToken);
        when(mockToken.getPayload()).thenReturn(mockPayload);
        when(mockPayload.getEmail()).thenReturn(VALID_EMAIL);

        UserAuth existingUser = UserAuth.newBuilder()
                .setEmail(VALID_EMAIL)
                .setGoogleToken("stored-token")
                .build();
        when(authRepository.getUserByEmail(VALID_EMAIL)).thenReturn(Optional.of(existingUser));

        AuthRequest request = AuthRequest.newBuilder()
                .setOperation(AuthOperationType.LOGIN_OAUTH)
                .setEmail(VALID_EMAIL)
                .setGoogleToken(tokenString)
                .setSessionToken(expectedToken)
                .build();

        when(sessionManager.createSession(eq(VALID_EMAIL), eq(expectedToken))).thenReturn(expectedToken);
        Message response = authHandler.handle(request.toByteArray(), true);
        AuthResponse authResponse = AuthResponse.parseFrom(response.getContent().toByteArray());

        assertTrue(authResponse.getSuccess());
        assertFalse(authResponse.getSessionToken().isEmpty());
    }

    @Test
    @DisplayName("Should fail OAuth login if user does not exist")
    void shouldFailOAuthLoginIfUserNotFound() throws Exception {
        String tokenString = "valid-google-token";

        GoogleIdToken mockToken = mock(GoogleIdToken.class);
        Payload mockPayload = mock(Payload.class);
        when(verifier.verify(tokenString)).thenReturn(mockToken);
        when(mockToken.getPayload()).thenReturn(mockPayload);
        when(mockPayload.getEmail()).thenReturn(VALID_EMAIL);

        when(authRepository.getUserByEmail(VALID_EMAIL)).thenReturn(Optional.empty());

        AuthRequest request = AuthRequest.newBuilder()
                .setOperation(AuthOperationType.LOGIN_OAUTH)
                .setEmail(VALID_EMAIL)
                .setGoogleToken(tokenString)
                .build();

        Message response = authHandler.handle(request.toByteArray(), true);
        AuthResponse authResponse = AuthResponse.parseFrom(response.getContent().toByteArray());

        assertFalse(authResponse.getSuccess());
        assertEquals("Invalid email or password", authResponse.getErrorMessage());
    }

    @Test
    @DisplayName("Should fail OAuth login if token validation fails")
    void shouldFailOAuthLoginIfTokenInvalid() throws Exception {
        String invalidToken = "bad-token";

        // Mock Verifier throwing IOException
        when(verifier.verify(invalidToken)).thenThrow(new IOException("Network error"));

        AuthRequest request = AuthRequest.newBuilder()
                .setOperation(AuthOperationType.LOGIN_OAUTH)
                .setEmail(VALID_EMAIL)
                .setGoogleToken(invalidToken)
                .build();

        Message response = authHandler.handle(request.toByteArray(), true);
        AuthResponse authResponse = AuthResponse.parseFrom(response.getContent().toByteArray());

        assertFalse(authResponse.getSuccess());
        assertTrue(authResponse.getErrorMessage().contains("Google Token error"));
    }

    // ========================================================================
    // Heartbeat Tests
    // ========================================================================

    @Test
    @DisplayName("Should successfully handle heartbeat for valid session")
    void shouldHandleHeartbeatSuccess() throws InvalidProtocolBufferException {
        String token = "valid-token";
        when(sessionManager.validateSession(token)).thenReturn(true);

        AuthRequest request = AuthRequest.newBuilder()
                .setOperation(AuthOperationType.HEARTBEAT)
                .setSessionToken(token)
                .build();

        Message response = authHandler.handle(request.toByteArray(), false);
        AuthResponse authResponse = AuthResponse.parseFrom(response.getContent().toByteArray());

        assertTrue(authResponse.getSuccess());
        verify(sessionManager).refreshSession(token);
    }

    @Test
    @DisplayName("Should fail heartbeat for invalid session")
    void shouldFailHeartbeatInvalidSession() throws InvalidProtocolBufferException {
        String token = "invalid-token";
        when(sessionManager.validateSession(token)).thenReturn(false);

        AuthRequest request = AuthRequest.newBuilder()
                .setOperation(AuthOperationType.HEARTBEAT)
                .setSessionToken(token)
                .build();

        Message response = authHandler.handle(request.toByteArray(), false);
        AuthResponse authResponse = AuthResponse.parseFrom(response.getContent().toByteArray());

        assertFalse(authResponse.getSuccess());
        verify(sessionManager, never()).refreshSession(anyString());
    }

}