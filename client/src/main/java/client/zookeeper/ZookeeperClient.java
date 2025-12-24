package client.zookeeper;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.ratis.client.RaftClient;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.protocol.RaftClientReply;
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.zookeeper.proto.MessageType;
import server.zookeeper.proto.MessageWrapper;
import server.zookeeper.proto.auth.AuthOperationType;
import server.zookeeper.proto.auth.AuthRequest;
import server.zookeeper.proto.auth.AuthResponse;
import server.zookeeper.proto.permissions.RequestType;
import server.zookeeper.proto.permissions.UserPermissions;
import server.zookeeper.proto.permissions.UserPermissionsRequest;
import server.zookeeper.proto.permissions.UserPermissionsResponse;
import server.zookeeper.proto.query.QueryResponse;
import server.zookeeper.proto.query.QueryType;
import server.zookeeper.proto.query.UserQuery;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class ZookeeperClient implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ZookeeperClient.class);

    private final RaftClient raftClient;
    private final SessionManager sessionManager;

    public void setSessionToken(String token) {
        sessionManager.startSession(token, this::sendHeartbeat);
    }

    public ZookeeperClient(RaftClient raftClient) {
        this.raftClient = raftClient;
        this.sessionManager = new SessionManager();
    }

    public AuthenticationResult register(String email, String password) {
        AuthRequest authRequest = RequestFactory.buildAuthRequest(
                AuthOperationType.REGISTER, email, password, sessionManager.getToken());
        return sendAuthRequest(authRequest, false);
    }

    public RaftClient getRaftClient() {
        return this.raftClient;
    }

    public AuthenticationResult login(String email, String password) {
        AuthRequest authRequest = RequestFactory.buildAuthRequest(
                AuthOperationType.LOGIN, email, password, sessionManager.getToken());
        AuthenticationResult result = sendAuthRequest(authRequest, false);

        if (result.isSuccess() && result.getSessionToken().isPresent()) {
            sessionManager.startSession(result.getSessionToken().get(), this::sendHeartbeat);
            LOG.info("User {} logged in successfully", email);
        }
        return result;
    }

    public AuthenticationResult registerOAuth(String email, String OAuthToken) {
        AuthRequest authRequest = RequestFactory.buildOAuthRequest(
                AuthOperationType.REGISTER_OAUTH, email, OAuthToken, sessionManager.getToken());
        return sendAuthRequest(authRequest, false);
    }

    public AuthenticationResult loginOAuth(String email, String OAuthToken) {
        AuthRequest authRequest = RequestFactory.buildOAuthRequest(
                AuthOperationType.LOGIN_OAUTH, email, OAuthToken, sessionManager.getToken());
        AuthenticationResult result = sendAuthRequest(authRequest, false);

        if (result.isSuccess() && result.getSessionToken().isPresent()) {
            sessionManager.startSession(result.getSessionToken().get(), this::sendHeartbeat);
            LOG.info("User {} logged in successfully", email);
        }
        return result;
    }

    private void sendHeartbeat() {
        Optional<String> token = sessionManager.getToken();
        if (token.isEmpty())
            return;

        AuthRequest request = RequestFactory.buildHeartbeatRequest(token.get());

        AuthenticationResult result = sendAuthRequest(request, false);

        if (!result.isSuccess()) {
            String msg = result.getMessage();
            if (isSessionError(msg)) {
                LOG.warn("Heartbeat failed with session error: {}. Logging out.", msg);
                sessionManager.invalidateSession();
            } else {
                LOG.warn("Heartbeat failed: {}", msg);
            }
        }
    }

    private boolean isSessionError(String msg) {
        return msg != null && (msg.contains("Unauthorized") || msg.contains("expired session"));
    }

    public boolean isAuthenticated() {
        return sessionManager.isAuthenticated();
    }

    public Optional<String> getSessionToken() {
        return sessionManager.getToken();
    }

    /**
     * Logout and invalidate the current session.
     * 
     * @return AuthenticationResult indicating success or failure
     */
    public AuthenticationResult logout() {
        Optional<String> token = sessionManager.getToken();
        if (token.isEmpty()) {
            LOG.warn("Logout called but no active session");
            return AuthenticationResult.failure("No active session to logout");
        }

        AuthRequest authRequest = RequestFactory.buildLogoutRequest(token.get());
        AuthenticationResult result = sendAuthRequest(authRequest, false);

        // Always invalidate local session regardless of server response
        sessionManager.invalidateSession();
        LOG.info("User logged out");

        return result;
    }

    private AuthenticationResult sendAuthRequest(AuthRequest authRequest, boolean isReadOnly) {
        return sendRequest(authRequest, MessageType.AUTH, isReadOnly, this::parseAuthResponse);
    }

    private QueryResult sendQueryRequest(UserQuery userQyery, boolean isReadOnly) {
        return sendRequest(userQyery, MessageType.QUERY, isReadOnly, this::parseQueryResponse);
    }

    private <T> T sendRequest(com.google.protobuf.Message request, MessageType type, boolean isReadOnly,
            Function<ByteString, T> responseParser) {
        try {
            MessageWrapper wrapper = MessageWrapper.newBuilder()
                    .setType(type)
                    .setPayload(request.toByteString())
                    .setSessionToken(sessionManager.getToken().orElseGet(String::new))
                    .build();
            Message message = Message.valueOf(ByteString.copyFrom(wrapper.toByteArray()));

            RaftClientReply reply = isReadOnly
                    ? raftClient.io().sendReadOnly(message)
                    : raftClient.io().send(message);
            if (!reply.isSuccess()) {
                return responseParser.apply(null);
            }
            return responseParser.apply(reply.getMessage().getContent());
        } catch (IOException e) {
            LOG.error("Request failed", e);
            return responseParser.apply(null);
        }
    }

    private AuthenticationResult parseAuthResponse(ByteString responseBytes) {
        try {
            AuthResponse authResponse = AuthResponse.parseFrom(responseBytes.asReadOnlyByteBuffer());

            return new AuthenticationResult(
                    authResponse.getSuccess(),
                    authResponse.getErrorMessage(),
                    authResponse.getSessionToken());

        } catch (InvalidProtocolBufferException e) {
            LOG.error("Failed to parse auth response", e);
            return AuthenticationResult.failure("Invalid server response");
        }
    }

    private QueryResult parseQueryResponse(ByteString responseBytes) {
        try {
            QueryResponse queryResponse = QueryResponse.parseFrom(responseBytes.asReadOnlyByteBuffer());

            return new QueryResult(
                    queryResponse.getSuccess(),
                    queryResponse.getErrorMessage(),
                    queryResponse.getValue());
        } catch (InvalidProtocolBufferException e) {
            LOG.error("Failed to parse query response");
            return QueryResult.failure("Invalid Server response");
        }
    }

    @Override
    public void close() throws IOException {
        // Logout to notify server and invalidate session
        if (isAuthenticated()) {
            logout();
        }
        sessionManager.close();
        if (raftClient != null) {
            raftClient.close();
        }
    }

    public QueryResult read(String key) {
        UserQuery q = RequestFactory.buildUserQuery(QueryType.GET, key, "", "", false, sessionManager.getToken());
        return sendQueryRequest(q, true);
    }

    public QueryResult read(String key, String directory) {
        UserQuery q = RequestFactory.buildUserQuery(QueryType.GET, key, "", directory, false,
                sessionManager.getToken());
        return sendQueryRequest(q, true);
    }

    public QueryResult create(String key, String value, boolean isEphemeral) {
        return create(key, value, "", isEphemeral);
    }

    public QueryResult create(String key, String value, String directory, boolean isEphemeral) {
        UserQuery q = RequestFactory.buildUserQuery(QueryType.CREATE, key, value, directory, isEphemeral,
                sessionManager.getToken());
        return sendQueryRequest(q, false);
    }

    public QueryResult update(String key, String value) {
        return update(key, value, "");
    }

    public QueryResult update(String key, String value, String directory) {
        UserQuery q = RequestFactory.buildUserQuery(QueryType.UPDATE, key, value, directory, false,
                sessionManager.getToken());
        return sendQueryRequest(q, false);
    }

    public QueryResult delete(String key) {
        UserQuery q = RequestFactory.buildUserQuery(QueryType.DELETE, key, "", "", false, sessionManager.getToken());
        return sendQueryRequest(q, false);
    }

    public QueryResult delete(String key, String directory) {
        UserQuery q = RequestFactory.buildUserQuery(QueryType.DELETE, key, "", directory, false,
                sessionManager.getToken());
        return sendQueryRequest(q, false);
    }

    private PermissionsResult sendPermissionsRequest(UserPermissionsRequest request, boolean isReadOnly) {
        return sendRequest(request, MessageType.PERMISSIONS, isReadOnly, this::parsePermissionsResponse);
    }

    public PermissionsResult getUserPermissionsByEmail(String email) {
        UserPermissionsRequest request = getUserPermissionsRequest(email, RequestType.GET,
                UserPermissions.newBuilder().build(), sessionManager.getToken().orElseGet(String::new));
        return sendPermissionsRequest(request, true);
    }

    private UserPermissionsRequest getUserPermissionsRequest(String email, RequestType requestType,
            UserPermissions userPermissions, String token) {
        return UserPermissionsRequest.newBuilder()
                .setRequestType(requestType)
                .setUserEmail(email == null ? "" : email)
                .setToken(token)
                .setUserPermissions(userPermissions)
                .build();
    }

    public PermissionsResult setIsAdmin(String email, boolean isAdmin) {
        UserPermissions userPerm = UserPermissions.newBuilder()
                .setIsAdmin(isAdmin)
                .build();
        UserPermissionsRequest request = getUserPermissionsRequest(email, RequestType.SET_IS_ADMIN,
                userPerm, sessionManager.getToken().orElseGet(String::new));
        return sendPermissionsRequest(request, false);
    }

    public PermissionsResult setCanCreateDirectories(String email, boolean canCreate) {
        UserPermissions userPerm = UserPermissions.newBuilder()
                .setCanCreateDirectories(canCreate)
                .build();
        UserPermissionsRequest request = getUserPermissionsRequest(email, RequestType.SET_CAN_CREATE_DIRECTORIES,
                userPerm, sessionManager.getToken().orElseGet(String::new));
        return sendPermissionsRequest(request, false);
    }

    public PermissionsResult setDirectoryPermissions(String email, Map<String, Integer> directoryPermissions) {
        UserPermissions.Builder permBuilder = UserPermissions.newBuilder();
        if (directoryPermissions != null && !directoryPermissions.isEmpty()) {
            permBuilder.putAllDirectoryPermissions(directoryPermissions);
        }
        UserPermissionsRequest request = getUserPermissionsRequest(email, RequestType.SET_DIRECTORY_PERMISSIONS,
                permBuilder.build(), sessionManager.getToken().orElseGet(String::new));
        return sendPermissionsRequest(request, false);
    }

    private PermissionsResult parsePermissionsResponse(ByteString responseBytes) {
        if (responseBytes == null) {
            return PermissionsResult.failure("Invalid server response");
        }
        try {
            UserPermissionsResponse resp = UserPermissionsResponse.parseFrom(responseBytes.asReadOnlyByteBuffer());
            return new PermissionsResult(resp.getSuccess(), resp.getErrorMessage(),
                    resp.hasUserPermissions() ? resp.getUserPermissions() : null);
        } catch (InvalidProtocolBufferException e) {
            LOG.error("Failed to parse permissions response", e);
            return PermissionsResult.failure("Invalid server response");
        }
    }

    public static class PermissionsResult {
        private final boolean success;
        private final String message;
        private final UserPermissions userPermissions;

        private PermissionsResult(boolean success, String message, UserPermissions userPermissions) {
            this.success = success;
            this.message = message;
            this.userPermissions = userPermissions;
        }

        public static PermissionsResult success(String message, UserPermissions perms) {
            return new PermissionsResult(true, message, perms);
        }

        public static PermissionsResult success(UserPermissions perms) {
            return new PermissionsResult(true, null, perms);
        }

        public static PermissionsResult failure(String message) {
            return new PermissionsResult(false, message, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public UserPermissions getUserPermissions() {
            return userPermissions;
        }

        @Override
        public String toString() {
            return String.format("PermissionsResult{success=%s, message='%s', userPermissions=%s}",
                    success, message, userPermissions);
        }
    }

    public static class AuthenticationResult {
        private final boolean success;
        private final String message;
        private final String sessionToken;

        private AuthenticationResult(boolean success, String message, String sessionToken) {
            this.success = success;
            this.message = message;
            this.sessionToken = sessionToken;
        }

        public static AuthenticationResult success(String message, String token) {
            return new AuthenticationResult(true, message, token);
        }

        public static AuthenticationResult success(String message) {
            return new AuthenticationResult(true, message, null);
        }

        public static AuthenticationResult failure(String message) {
            return new AuthenticationResult(false, message, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public Optional<String> getSessionToken() {
            return Optional.ofNullable(sessionToken);
        }

        @Override
        public String toString() {
            return String.format("AuthenticationResult{success=%s, message='%s', hasToken=%s}",
                    success, message, sessionToken != null);
        }
    }

    public static class QueryResult {
        private final boolean success;
        private final String message;
        private final String value;

        private QueryResult(boolean success, String message, String value) {
            this.success = success;
            this.message = message;
            this.value = value;
        }

        public static QueryResult success(String message, String value) {
            return new QueryResult(true, message, value);
        }

        public static QueryResult success(String message) {
            return new QueryResult(true, message, null);
        }

        public static QueryResult failure(String message) {
            return new QueryResult(false, message, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return String.format("QueryResult{success=%s, message='%s', value=%s}",
                    success, message, value);
        }
    }
}