package server.zookeeper.modules;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.zookeeper.DB.AuthRepository;
import server.zookeeper.DB.SessionRepository;
import server.zookeeper.proto.auth.UserAuth;
import server.zookeeper.proto.permissions.RequestType;
import server.zookeeper.proto.permissions.UserPermissions;
import server.zookeeper.proto.permissions.UserPermissionsRequest;
import server.zookeeper.proto.permissions.UserPermissionsResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public class AuthzHandler implements MessageHandler {
    private static final Logger LOG = LoggerFactory.getLogger(AuthzHandler.class);
    private final SessionRepository sessionRepository;
    private final AuthRepository authRepository;
    private static final String HANDLER_TYPE = "AUTHZ";
    private final Map<RequestType, UserPermissionHandler> handlers;

    public AuthzHandler(SessionRepository sessionRepository, AuthRepository authRepository) {
        this.sessionRepository = requireNonNull(sessionRepository, "sessionRepository Can't be Null");
        this.authRepository = requireNonNull(authRepository, "authRepository Can't be Null");
        this.handlers = new HashMap<>();
        addHandlers();
    }

    private void addHandlers() {
        handlers.put(RequestType.SET_CAN_CREATE_DIRECTORIES, this::setCanCreateDirectories);
        handlers.put(RequestType.SET_IS_ADMIN, this::setISAdmin);
        handlers.put(RequestType.SET_DIRECTORY_PERMISSIONS, this::setDirectoriesPermissions);
        handlers.put(RequestType.GET, this::getUserPermissionsByEmail);
    }

    @FunctionalInterface
    private interface UserPermissionHandler {
        UserPermissionsResponse handle(UserPermissionsRequest request);
    }

    @Override
    public Message handle(byte[] payload, boolean isMutation) {
        LOG.debug("Handling User Permission request, isMutation: {}", isMutation);

        try {
            UserPermissionsRequest request = UserPermissionsRequest.parseFrom(payload);
            LOG.debug("Parsed UserPermissions request: operation={}", request.getRequestType());

            UserPermissionsResponse response = routeRequest(request);
            return Message.valueOf(ByteString.copyFrom(response.toByteArray()));
        } catch (InvalidProtocolBufferException e) {
            LOG.error("Failed to parse AuthRequest from payload", e);
            return Message.valueOf(ByteString.copyFrom(createErrorResponse("Invalid request format: " + e.getMessage()).toByteArray()));

        } catch (Exception e) {
            LOG.error("Unexpected error handling auth request", e);
            return Message.valueOf(ByteString.copyFrom(createErrorResponse("Internal server error: " + e.getMessage()).toByteArray()));
        }
    }

    private UserPermissionsResponse routeRequest(UserPermissionsRequest request) {
        RequestType operation = request.getRequestType();

        if (operation == RequestType.REQUEST_TYPE_UNSPECIFIED) {
            LOG.warn("Received request with UNSPECIFIED operation");
            return createErrorResponse("Invalid operation type");
        }

        AuthzHandler.UserPermissionHandler handler = handlers.get(operation);

        if (handler == null) {
            LOG.warn("No handler registered for operation: {}", operation);
            return createErrorResponse("Unsupported operation: " + operation);
        }

        return handler.handle(request);
    }

    private UserPermissionsResponse createErrorResponse(String errorMessage) {
        return UserPermissionsResponse.newBuilder()
                .setSuccess(false)
                .setErrorMessage(errorMessage)
                .build();
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
            UserPermissionsRequest request = UserPermissionsRequest.parseFrom(payload);
            return request.getRequestType() != RequestType.REQUEST_TYPE_UNSPECIFIED;
        } catch (InvalidProtocolBufferException e) {
            LOG.debug("Payload cannot be parsed as UserPermissionsRequest", e);
            return false;
        }
    }

    public Optional<UserPermissions> getUserPermissionsByToken(String token) {
        return Optional.empty();
    }

    public UserPermissionsResponse getUserPermissionsByEmail(UserPermissionsRequest request) {
        String userEmail = request.getUserEmail();

        Optional<UserAuth> user = authRepository.getUserByEmail(userEmail);
        return user.map(AuthzHandler::getSuccessUserPermissionsResponse).orElseGet(() -> getFailedUserPermissionsResponse(userEmail));
    }

    public UserPermissionsResponse setISAdmin(UserPermissionsRequest request) {
        String userEmail = request.getUserEmail();
        boolean isAdmin = request.getUserPermissions().getIsAdmin();

        Optional<UserAuth> optUser = authRepository.getUserByEmail(userEmail);
        if (optUser.isEmpty()) {
            return getFailedUserPermissionsResponse(userEmail);
        }
        UserAuth user = optUser.get().toBuilder()
                .setIsAdmin(isAdmin)
                .build();
        authRepository.updateUser(user);

        return getSuccessUserPermissionsResponse(user);
    }

    public UserPermissionsResponse setCanCreateDirectories(UserPermissionsRequest request) {
        String userEmail = request.getUserEmail();
        boolean canCreateDirectories = request.getUserPermissions().getCanCreateDirectories();

        Optional<UserAuth> optUser = authRepository.getUserByEmail(userEmail);
        if (optUser.isEmpty()) {
            return getFailedUserPermissionsResponse(userEmail);
        }
        UserAuth user = optUser.get().toBuilder()
                .setCanCreateDirectories(canCreateDirectories)
                .build();
        authRepository.updateUser(user);

        return getSuccessUserPermissionsResponse(user);
    }

    public UserPermissionsResponse setDirectoriesPermissions(UserPermissionsRequest request) {
        String userEmail = request.getUserEmail();
        Map<String, Integer> directoriesPermissions = request.getUserPermissions().getDirectoryPermissionsMap();

        Optional<UserAuth> optUser = authRepository.getUserByEmail(userEmail);
        if (optUser.isEmpty()) {
            return getFailedUserPermissionsResponse(userEmail);
        }
        UserAuth user = optUser.get().toBuilder()
                .putAllPermissions(directoriesPermissions)
                .build();
        authRepository.updateUser(user);

        return getSuccessUserPermissionsResponse(user);
    }

    private static UserPermissionsResponse getFailedUserPermissionsResponse(String userEmail) {
        return UserPermissionsResponse.newBuilder()
                .setSuccess(false)
                .setErrorMessage("no such user with email " + userEmail)
                .build();
    }

    private static UserPermissionsResponse getSuccessUserPermissionsResponse(UserAuth user) {
        return UserPermissionsResponse.newBuilder()
                .setSuccess(true)
                .setUserPermissions(
                        UserPermissions.newBuilder()
                                .setCanCreateDirectories(user.getCanCreateDirectories())
                                .setIsAdmin(user.getIsAdmin())
                                .putAllDirectoryPermissions(user.getPermissionsMap())
                                .build()
                )
                .build();
    }

}
