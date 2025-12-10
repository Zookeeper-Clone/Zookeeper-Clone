package server.zookeeper.modules;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.zookeeper.DB.AuthRepository;
import server.zookeeper.proto.auth.*;
import server.zookeeper.util.EmailUtils;
import server.zookeeper.util.PasswordHasher;

import java.util.*;

public class AuthHandler implements MessageHandler {
    private static final Logger LOG = LoggerFactory.getLogger(AuthHandler.class);
    private static final String HANDLER_TYPE = "AUTH";
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 128;
    public static final String LOGIN_VALIDATION_FAILED = "Login validation failed: {}";
    private final AuthRepository authRepository;
    private final PasswordHasher passwordHasher;
    private final GoogleIdTokenVerifier verifier;
    private final Map<AuthOperationType, AuthOperationHandler> operationHandlers;

    @FunctionalInterface
    private interface AuthOperationHandler {
        AuthResponse handle(AuthRequest request);
    }

    public AuthHandler(AuthRepository authRepository, PasswordHasher passwordHasher, GoogleIdTokenVerifier verifier) {
        if (authRepository == null) {
            throw new IllegalArgumentException("AuthRepository cannot be null");
        }
        if (passwordHasher == null) {
            throw new IllegalArgumentException("PasswordHasher cannot be null");
        }
        if (verifier == null){
            throw new IllegalArgumentException("GoogleIdTokenVerifiter cannot be null");
        }
        this.authRepository = authRepository;
        this.passwordHasher = passwordHasher;
        this.verifier = verifier;
        this.operationHandlers = initializeOperationHandlers();

        LOG.info("AuthHandler initialized successfully");
    }

    private Map<AuthOperationType, AuthOperationHandler> initializeOperationHandlers() {
        Map<AuthOperationType, AuthOperationHandler> handlers = new HashMap<>();
        handlers.put(AuthOperationType.REGISTER, this::handleRegister);
        handlers.put(AuthOperationType.LOGIN, this::handleLogin);
        handlers.put(AuthOperationType.REGISTER_OAUTH, this::handleRegisterOAuth);
        handlers.put(AuthOperationType.LOGIN_OAUTH, this::handleLoginOAuth);
        handlers.put(AuthOperationType.LOGOUT, this::handleLogout);
        handlers.put(AuthOperationType.CHANGE_PASSWORD, this::handleChangePassword);
        // handlers.put(AuthOperationType.MODIFY_PERMISSIONS, this::handleModifyPermissions); // Future: Admin only
        return handlers;
    }

    @Override
    public Message handle(byte[] payload, boolean isMutation) {
        LOG.debug("Handling authentication request, isMutation: {}", isMutation);

        try {
            AuthRequest request = AuthRequest.parseFrom(payload);
            LOG.debug("Parsed auth request: operation={}, email={}",
                    request.getOperation(), EmailUtils.maskEmail(request.getEmail()));

            AuthResponse response = routeRequest(request);
            return Message.valueOf(ByteString.copyFrom(response.toByteArray()));
        } catch (InvalidProtocolBufferException e) {
            LOG.error("Failed to parse AuthRequest from payload", e);
            return createErrorResponse("Invalid request format: " + e.getMessage());

        } catch (Exception e) {
            LOG.error("Unexpected error handling auth request", e);
            return createErrorResponse("Internal server error: " + e.getMessage());
        }
    }

    @Override
    public String getHandlerType() {
        return HANDLER_TYPE;
    }


    @Override
    public boolean canHandle(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return false;
        }

        try {
            AuthRequest request = AuthRequest.parseFrom(payload);
            return request.getOperation() != AuthOperationType.AUTH_UNSPECIFIED;
        } catch (InvalidProtocolBufferException e) {
            LOG.debug("Payload cannot be parsed as AuthRequest", e);
            return false;
        }
    }

    private AuthResponse routeRequest(AuthRequest request) {
        AuthOperationType operation = request.getOperation();

        if (operation == AuthOperationType.AUTH_UNSPECIFIED) {
            LOG.warn("Received request with UNSPECIFIED operation");
            return createErrorAuthResponse("Invalid operation type");
        }

        AuthOperationHandler handler = operationHandlers.get(operation);

        if (handler == null) {
            LOG.warn("No handler registered for operation: {}", operation);
            return createErrorAuthResponse("Unsupported operation: " + operation);
        }

        return handler.handle(request);
    }

    private AuthResponse handleRegister(AuthRequest request) {
        LOG.debug("Processing REGISTER request for: {}", EmailUtils.maskEmail(request.getEmail()));

        try {
            ValidationResult validation = validateRegistrationRequest(request);
            if (!validation.isValid()) {
                LOG.warn("Registration validation failed: {}", validation.getErrorMessage());
                return createErrorAuthResponse(validation.getErrorMessage());
            }

            String email = request.getEmail().trim().toLowerCase();

            if (authRepository.userExists(email)) {
                LOG.warn("Registration failed: User already exists: {}", EmailUtils.maskEmail(email));
                return createErrorAuthResponse("User with this email already exists");
            }

            String passwordHash = passwordHasher.hashPassword(request.getPassword());
            UserAuth userAuth = UserAuth.newBuilder()
                    .setEmail(email)
                    .setPasswordHash(passwordHash)
                    .setIsAdmin(false)
                    .setCanCreateDirectories(false)
                    .build();
            authRepository.saveUser(userAuth);

            LOG.info("Successfully registered user: {}", EmailUtils.maskEmail(email));

            String sessionToken = generateSessionToken();
            UserInfo userInfo = convertToUserInfo(userAuth);
            return AuthResponse.newBuilder()
                    .setSuccess(true)
                    .setSessionToken(sessionToken)
                    .setErrorMessage("")
                    .setUserInfo(userInfo)
                    .build();
        } catch (Exception e) {
            LOG.error("Error during registration", e);
            return createErrorAuthResponse("Registration failed: " + e.getMessage());
        }
    }
    private AuthResponse handleRegisterOAuth(AuthRequest request){
        LOG.debug("Processing REGISTER_OAUTH request for: {}", EmailUtils.maskEmail(request.getEmail()));
        try {
            ValidationResult validationResult = validateOAuthRegistrationRequest(request);
            if(!validationResult.isValid()){
                LOG.warn("Registration validation failed: {}", validationResult.getErrorMessage());
                return createErrorAuthResponse(validationResult.getErrorMessage());
            }
            String email = request.getEmail().trim().toLowerCase();
            if(authRepository.userExists(email)){
                LOG.warn("Registration failed: User already exists: {}", EmailUtils.maskEmail(email));
                return createErrorAuthResponse("User with this email already exists");
            }
            UserAuth userAuth = UserAuth.newBuilder()
                    .setEmail(email)
                    .setGoogleToken(request.getGoogleToken())
                    .setIsAdmin(false)
                    .setCanCreateDirectories(false)
                    .build();
            authRepository.saveOAuthUser(userAuth);
            UserInfo userInfo = convertToUserInfo(userAuth);
            String sessionToken = generateSessionToken();
            return AuthResponse.newBuilder()
                    .setSuccess(true)
                    .setUserInfo(userInfo)
                    .setSessionToken(sessionToken)
                    .setErrorMessage("")
                    .build();
        }catch (Exception e){
            LOG.error("Error during registration", e);
            return createErrorAuthResponse("Registration failed: " + e.getMessage());
        }
    }
    private AuthResponse handleLogout(AuthRequest request) {
        LOG.debug("Processing LOGOUT request");
        // TODO: Implement session invalidation when session management is added
        return AuthResponse.newBuilder()
                .setSuccess(true)
                .setErrorMessage("")
                .build();
    }
    private AuthResponse handleChangePassword(AuthRequest request) {
        LOG.debug("Processing CHANGE_PASSWORD request for: {}", EmailUtils.maskEmail(request.getEmail()));
        // FIXME: Use chain of responsibility
        try {
            ValidationResult emailValidation = validateChangePasswordRequest(request);
            if (!emailValidation.isValid()) {
                LOG.warn("Password change validation failed: {}", emailValidation.getErrorMessage());
                return createErrorAuthResponse(emailValidation.getErrorMessage());
            }

            String email = request.getEmail().trim().toLowerCase();

            Optional<UserAuth> userOptional = authRepository.getUserByEmail(email);

            if (userOptional.isEmpty()) {
                LOG.warn("Password change failed: User not found: {}", EmailUtils.maskEmail(email));
                return createErrorAuthResponse("Invalid credentials");
            }

            UserAuth user = userOptional.get();
            boolean currentPasswordValid = passwordHasher.verifyPassword(
                    request.getPassword(),
                    user.getPasswordHash()
            );
            if (!currentPasswordValid) {
                LOG.warn("Password change failed: Invalid current password for: {}",
                        EmailUtils.maskEmail(email));
                return createErrorAuthResponse("Invalid credentials");
            }

            ValidationResult newPasswordValidation = validatePassword(request.getNewPassword());
            if (!newPasswordValidation.isValid()) {
                LOG.warn("Password change failed: New password validation failed: {}",
                        newPasswordValidation.getErrorMessage());
                return createErrorAuthResponse("New password invalid: " +
                        newPasswordValidation.getErrorMessage());
            }

            boolean samePassword = passwordHasher.verifyPassword(
                    request.getNewPassword(),
                    user.getPasswordHash()
            );

            if (samePassword) {
                LOG.warn("Password change failed: New password same as current for: {}",
                        EmailUtils.maskEmail(email));
                return createErrorAuthResponse("New password must be different from current password");
            }

            String newPasswordHash = passwordHasher.hashPassword(request.getNewPassword());
            UserAuth updatedUser = getUserAuth(user, newPasswordHash);
            authRepository.updateUser(updatedUser);
            LOG.info("Successfully changed password for user: {}", EmailUtils.maskEmail(email));

            UserInfo userInfo = convertToUserInfo(updatedUser);
            return AuthResponse.newBuilder()
                    .setSuccess(true)
                    .setErrorMessage("")
                    .setUserInfo(userInfo)
                    .build();
        } catch (Exception e) {
            LOG.error("Error during password change", e);
            return createErrorAuthResponse("Password change failed: " + e.getMessage());
        }
    }

    private static UserAuth getUserAuth(UserAuth user, String newPasswordHash) {
        return UserAuth.newBuilder()
                .setEmail(user.getEmail())
                .setPasswordHash(newPasswordHash)
                .setIsAdmin(user.getIsAdmin())
                .putAllPermissions(user.getPermissionsMap())
                .setCanCreateDirectories(user.getCanCreateDirectories())
                .build();
    }

    private ValidationResult validateChangePasswordRequest(AuthRequest request) {
        ValidationResult Invalid_email_format = validateEmail(request);
        if (Invalid_email_format != null) return Invalid_email_format;

        if (request.getPassword() == null || request.getPassword().isEmpty()) {
            return ValidationResult.failure("Current password is required");
        }

        if (request.getNewPassword() == null || request.getNewPassword().isEmpty()) {
            return ValidationResult.failure("New password is required");
        }

        return ValidationResult.success();
    }

    private AuthResponse handleLogin(AuthRequest request) {
        LOG.debug("Processing LOGIN request for: {}", EmailUtils.maskEmail(request.getEmail()));

        try {
            ValidationResult validation = validateLoginRequest(request);
            if (!validation.isValid()) {
                LOG.warn(LOGIN_VALIDATION_FAILED, validation.getErrorMessage());
                return createErrorAuthResponse(validation.getErrorMessage());
            }

            String email = request.getEmail().trim().toLowerCase();

            Optional<UserAuth> userOptional = authRepository.getUserByEmail(email);

            if (userOptional.isEmpty()) {
                LOG.warn("Login failed: User not found: {}", EmailUtils.maskEmail(email));
                return createErrorAuthResponse("Invalid email or password");
            }

            UserAuth user = userOptional.get();

            boolean passwordValid = passwordHasher.verifyPassword(
                    request.getPassword(),
                    user.getPasswordHash()
            );

            if (!passwordValid) {
                LOG.warn("Login failed: Invalid password for: {}", EmailUtils.maskEmail(email));
                return createErrorAuthResponse("Invalid email or password");
            }

            // TODO: Implement proper session management
            String sessionToken = generateSessionToken();

            LOG.info("Successfully logged in user: {}", EmailUtils.maskEmail(email));

            UserInfo userInfo = convertToUserInfo(user);
            return AuthResponse.newBuilder()
                    .setSuccess(true)
                    .setErrorMessage("")
                    .setSessionToken(sessionToken)
                    .setUserInfo(userInfo)
                    .build();

        } catch (Exception e) {
            LOG.error("Error during login", e);
            return createErrorAuthResponse("Login failed: " + e.getMessage());
        }
    }
    private  AuthResponse handleLoginOAuth(AuthRequest request){
        LOG.debug("Processing LOGIN_OAUTH request for: {}", EmailUtils.maskEmail(request.getEmail()));
        try {
            ValidationResult validation = validateOAuthLoginRequest(request);
            if (!validation.isValid()) {
                LOG.warn(LOGIN_VALIDATION_FAILED, validation.getErrorMessage());
                return createErrorAuthResponse(validation.getErrorMessage());
            }
            String email = request.getEmail().trim().toLowerCase();
            Optional<UserAuth> userOptional = authRepository.getUserByEmail(email);
            if (userOptional.isEmpty()){
                LOG.warn("Login failed: User not found: {}", EmailUtils.maskEmail(email));
                return createErrorAuthResponse("Invalid email or password");
            }
            
            UserAuth userAuth = userOptional.get();
            String sessionToken = generateSessionToken();

            LOG.info("Successfully logged in OAUTH user: {}", EmailUtils.maskEmail(email));
                UserInfo userInfo = convertToUserInfo(userAuth);
                return AuthResponse.newBuilder()
                        .setSuccess(true)
                        .setErrorMessage("")
                        .setSessionToken(sessionToken)
                        .setUserInfo(userInfo)
                        .build();

        }catch (Exception e){
            LOG.error("Error during login", e);
            return createErrorAuthResponse("Login failed: " + e.getMessage());
        }
    }
    private String generateSessionToken() {
        return UUID.randomUUID().toString();
    }
    private ValidationResult validateOAuthLoginRequest(AuthRequest request){
        ValidationResult Invalid_email_format = validateEmail(request);
        if (Invalid_email_format != null) return Invalid_email_format;

        if(request.getGoogleToken() == null || request.getGoogleToken().isEmpty()){
            return ValidationResult.failure("Google Token cannot be empty");
        }

        try {
            GoogleIdToken token = verifier.verify(request.getGoogleToken());
            if(!token.getPayload().getEmail().equals(request.getEmail())){
                return ValidationResult.failure("Google Token doesn't match email");
            }
        } catch (Exception e) {
                return ValidationResult.failure("Google Token error");
        }
        return ValidationResult.success();
    }

    private static ValidationResult validateEmail(AuthRequest request) {
        try {
            EmailUtils.validateEmail(request.getEmail());
        } catch (RuntimeException e){
            return ValidationResult.failure("Invalid email format");
        }
        return null;
    }

    private ValidationResult validateLoginRequest(AuthRequest request) {
        ValidationResult Invalid_email_format = validateEmail(request);
        if (Invalid_email_format != null) return Invalid_email_format;


        if (request.getPassword() == null || request.getPassword().isEmpty()) {
            return ValidationResult.failure("Password cannot be empty");
        }

        return ValidationResult.success();
    }

    private UserInfo convertToUserInfo(UserAuth userAuth) {
        return UserInfo.newBuilder()
                .setEmail(userAuth.getEmail())
                .setIsAdmin(userAuth.getIsAdmin())
                .putAllPermissions(userAuth.getPermissionsMap())
                .setCanCreateDirectories(userAuth.getCanCreateDirectories())
                .build();
    }

    private static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;

        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        static ValidationResult success() {
            return new ValidationResult(true, "");
        }

        static ValidationResult failure(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }

        boolean isValid() {
            return valid;
        }

        String getErrorMessage() {
            return errorMessage;
        }
    }
    private ValidationResult validateOAuthRegistrationRequest(AuthRequest request){
        ValidationResult Invalid_email_format = validateEmail(request);
        if (Invalid_email_format != null) return Invalid_email_format;
        try {
            GoogleIdToken token = verifier.verify(request.getGoogleToken());
            if(!token.getPayload().getEmail().equals(request.getEmail())){
                return ValidationResult.failure("Token Email doesn't match request email");
            }
        } catch (Exception e) {
            return ValidationResult.failure("Invalid Google format");
        }
        return ValidationResult.success();
    }
    private ValidationResult validateRegistrationRequest(AuthRequest request) {
        ValidationResult Invalid_email_format = validateEmail(request);
        if (Invalid_email_format != null) return Invalid_email_format;

        ValidationResult passwordValidation = validatePassword(request.getPassword());
        if (!passwordValidation.isValid()) {
            return passwordValidation;
        }

        return ValidationResult.success();
    }

    private ValidationResult validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            return ValidationResult.failure("Password cannot be empty");
        }

        if (password.length() < MIN_PASSWORD_LENGTH) {
            return ValidationResult.failure(
                    String.format("Password must be at least %d characters long", MIN_PASSWORD_LENGTH)
            );
        }

        if (password.length() > MAX_PASSWORD_LENGTH) {
            return ValidationResult.failure(
                    String.format("Password must not exceed %d characters", MAX_PASSWORD_LENGTH)
            );
        }

        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);

        if (!hasLetter || !hasDigit) {
            return ValidationResult.failure("Password must contain both letters and numbers");
        }

        return ValidationResult.success();
    }

    private Message createErrorResponse(String errorMessage) {
        AuthResponse response = createErrorAuthResponse(errorMessage);
        return Message.valueOf(ByteString.copyFrom(response.toByteArray()));
    }

    private AuthResponse createErrorAuthResponse(String errorMessage) {
        return AuthResponse.newBuilder()
                .setSuccess(false)
                .setErrorMessage(errorMessage)
                .build();
    }
}
