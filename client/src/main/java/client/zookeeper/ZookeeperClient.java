package client.zookeeper;

import client.zookeeper.watches.Watcher;
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
import server.zookeeper.proto.metrics.MetricsProto;
import server.zookeeper.proto.metrics.MetricsProto.MetricsResponse;
import server.zookeeper.proto.permissions.RequestType;
import server.zookeeper.proto.permissions.UserPermissions;
import server.zookeeper.proto.permissions.UserPermissionsRequest;
import server.zookeeper.proto.permissions.UserPermissionsResponse;
import server.zookeeper.proto.query.QueryResponse;
import server.zookeeper.proto.query.QueryType;
import server.zookeeper.proto.query.UserQuery;
import server.zookeeper.proto.query.WatchEvent;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class ZookeeperClient implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ZookeeperClient.class);

    private final RaftClient raftClient;
    private final SessionManager sessionManager;
    private final WatchHandler watchHandler = new WatchHandler();
    private final Watcher watcher;
    private volatile String currentUserEmail;
    private final AtomicInteger consecutiveHeartbeatFailures = new AtomicInteger(0);
    private static final int MAX_CONSECUTIVE_HEARTBEAT_FAILURES = 3;

    public void setSessionToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Session token cannot be null or empty");
        }
        sessionManager.startSession(token.trim(), this::sendHeartbeat);
    }

    public Optional<String> getCurrentUserEmail() {
        return Optional.ofNullable(currentUserEmail);
    }

    public ZookeeperClient(RaftClient raftClient, Watcher watcher) {
        this.raftClient = raftClient;
        this.watcher = watcher;
        this.sessionManager = new SessionManager();
    }

    public AuthenticationResult register(String email, String password) {
        if (email == null || email.trim().isEmpty() || password == null || password.isEmpty()) {
            return AuthenticationResult.failure("Email and password cannot be null or empty");
        }
        AuthRequest authRequest;
        try {
            authRequest = RequestFactory.buildAuthRequest(
                    AuthOperationType.REGISTER, email.trim(), password, sessionManager.getToken());
        } catch (IllegalArgumentException e) {
            return AuthenticationResult.failure(e.getMessage());
        }
        return sendAuthRequest(authRequest, false);
    }

    public RaftClient getRaftClient() {
        return this.raftClient;
    }

    public AuthenticationResult login(String email, String password) {
        if (email == null || email.trim().isEmpty() || password == null || password.isEmpty()) {
            return AuthenticationResult.failure("Email and password cannot be null or empty");
        }
        AuthRequest authRequest;
        try {
            authRequest = RequestFactory.buildAuthRequest(
                    AuthOperationType.LOGIN, email.trim(), password, sessionManager.getToken());
        } catch (IllegalArgumentException e) {
            return AuthenticationResult.failure(e.getMessage());
        }
        AuthenticationResult result = sendAuthRequest(authRequest, false);

        if (result.isSuccess()) {
            if (result.getSessionToken().isPresent() && !result.getSessionToken().get().trim().isEmpty()) {
                this.currentUserEmail = email.trim();
                sessionManager.startSession(result.getSessionToken().get().trim(), this::sendHeartbeat);
                LOG.info("User {} logged in successfully", email);
            } else {
                return AuthenticationResult.failure("Login succeeded on server but missing session token");
            }
        }
        return result;
    }

    public AuthenticationResult registerOAuth(String email, String OAuthToken) {
        if (email == null || email.trim().isEmpty() || OAuthToken == null || OAuthToken.trim().isEmpty()) {
            return AuthenticationResult.failure("Email and OAuth token cannot be null or empty");
        }
        AuthRequest authRequest;
        try {
            authRequest = RequestFactory.buildOAuthRequest(
                    AuthOperationType.REGISTER_OAUTH, email.trim(), OAuthToken.trim(), sessionManager.getToken());
        } catch (IllegalArgumentException e) {
            return AuthenticationResult.failure(e.getMessage());
        }
        return sendAuthRequest(authRequest, false);
    }

    public AuthenticationResult loginOAuth(String email, String OAuthToken) {
        if (email == null || email.trim().isEmpty() || OAuthToken == null || OAuthToken.trim().isEmpty()) {
            return AuthenticationResult.failure("Email and OAuth token cannot be null or empty");
        }
        AuthRequest authRequest;
        try {
            authRequest = RequestFactory.buildOAuthRequest(
                    AuthOperationType.LOGIN_OAUTH, email.trim(), OAuthToken.trim(), sessionManager.getToken());
        } catch (IllegalArgumentException e) {
            return AuthenticationResult.failure(e.getMessage());
        }
        AuthenticationResult result = sendAuthRequest(authRequest, false);

        if (result.isSuccess()) {
            if (result.getSessionToken().isPresent() && !result.getSessionToken().get().trim().isEmpty()) {
                this.currentUserEmail = email.trim();
                sessionManager.startSession(result.getSessionToken().get().trim(), this::sendHeartbeat);
                LOG.info("User {} logged in successfully", email);
            } else {
                return AuthenticationResult.failure("Login succeeded on server but missing session token");
            }
        }
        return result;
    }

    private void sendHeartbeat() {
        Optional<String> token = sessionManager.getToken();
        if (token.isEmpty())
            return;

        AuthRequest request;
        try {
            request = RequestFactory.buildHeartbeatRequest(token.get());
        } catch (IllegalArgumentException e) {
            LOG.warn("Failed to build heartbeat request: {}", e.getMessage());
            return;
        }

        AuthenticationResult result = sendAuthRequest(request, false);

        if (result.isSuccess()) {
            consecutiveHeartbeatFailures.set(0);
        } else {
            int failures = consecutiveHeartbeatFailures.incrementAndGet();
            String msg = result.getMessage();
            if (isSessionError(msg) || failures >= MAX_CONSECUTIVE_HEARTBEAT_FAILURES) {
                LOG.warn("Heartbeat failed (failures: {}, msg: {}). Invalidating session.", failures, msg);
                sessionManager.invalidateSession();
                currentUserEmail = null;
            } else {
                LOG.warn("Heartbeat failed (attempt {}): {}", failures, msg);
            }
        }
    }

    private boolean isSessionError(String msg) {
        if (msg == null) return false;
        String lower = msg.toLowerCase();
        return lower.contains("unauthorized") || lower.contains("expired session")
                || lower.contains("invalid session") || lower.contains("session expired")
                || lower.contains("session not found");
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
        currentUserEmail = null;
        LOG.info("User logged out");

        return result;
    }

    private AuthenticationResult sendAuthRequest(AuthRequest authRequest, boolean isReadOnly) {
        return sendRequest(authRequest, MessageType.AUTH, isReadOnly, this::parseAuthResponse);
    }

    private QueryResult sendQueryRequest(UserQuery userQuery, boolean isReadOnly) {
        return sendRequest(userQuery, MessageType.QUERY, isReadOnly, this::parseQueryResponse);
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

            if (reply == null || !reply.isSuccess() || reply.getMessage() == null) {
                return responseParser.apply(null);
            }
            return responseParser.apply(reply.getMessage().getContent());
        } catch (Exception e) {
            LOG.error("Request failed", e);
            return responseParser.apply(null);
        }
    }

    private CompletableFuture<RaftClientReply> sendAsyncRequest(com.google.protobuf.Message request, MessageType type, boolean isReadOnly) {
        try {
            MessageWrapper wrapper = MessageWrapper.newBuilder()
                    .setType(type)
                    .setPayload(request.toByteString())
                    .setSessionToken(sessionManager.getToken().orElseGet(String::new))
                    .build();
            Message message = Message.valueOf(ByteString.copyFrom(wrapper.toByteArray()));

            return isReadOnly
                    ? raftClient.async().sendReadOnly(message)
                    : raftClient.async().send(message);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private AuthenticationResult parseAuthResponse(ByteString responseBytes) {
        if (responseBytes == null) {
            return AuthenticationResult.failure("Transport failure or request rejected by cluster");
        }
        try {
            AuthResponse authResponse = AuthResponse.parseFrom(responseBytes.asReadOnlyByteBuffer());
            boolean success = authResponse.getSuccess();
            String error = authResponse.getErrorMessage();
            String message = success ? (error != null && !error.isEmpty() ? error : "Success")
                                     : (error != null && !error.isEmpty() ? error : "Authentication failed");
            return new AuthenticationResult(
                    success,
                    message,
                    authResponse.getSessionToken());

        } catch (InvalidProtocolBufferException e) {
            LOG.error("Failed to parse auth response", e);
            return AuthenticationResult.failure("Invalid server response");
        }
    }

    private QueryResult parseQueryResponse(ByteString responseBytes) {
        if (responseBytes == null) {
            return QueryResult.failure("Transport failure or request rejected by cluster");
        }
        try {
            QueryResponse queryResponse = QueryResponse.parseFrom(responseBytes.asReadOnlyByteBuffer());

            return new QueryResult(
                    queryResponse.getSuccess(),
                    queryResponse.getErrorMessage(),
                    queryResponse.getValue());
        } catch (InvalidProtocolBufferException e) {
            LOG.error("Failed to parse query response", e);
            return QueryResult.failure("Invalid Server response");
        }
    }

    public MetricsResult getMetrics() {
        return sendRequest(MetricsProto.MetricsResponse.getDefaultInstance(), MessageType.METRICS, true,
                this::parseMetricsResponse);
    }

    private MetricsResult parseMetricsResponse(ByteString responseBytes) {
        if (responseBytes == null) {
            return MetricsResult.failure("Transport failure or request rejected by cluster");
        }
        try {
            MetricsResponse resp = MetricsResponse.parseFrom(responseBytes.asReadOnlyByteBuffer());
            if (!resp.getSuccess()) {
                return MetricsResult.failure(resp.getErrorMessage());
            }
            return new MetricsResult(true, null, resp.getElectionCount(), resp.getTimeoutCount(),
                    resp.getClientReadRequests(), resp.getClientWriteRequests(), resp.getNumPendingRequestsInQueue(),
                    resp.getNumFailedClientReadOnServer(), resp.getAppendEntryLatency());
        } catch (InvalidProtocolBufferException e) {
            LOG.error("Failed to parse metrics response", e);
            return MetricsResult.failure("Invalid server response");
        }
    }

    public QueryResult read(String key) {
        return read(key, "");
    }

    public QueryResult read(String key, String directory) {
        if (key == null || key.trim().isEmpty()) {
            return QueryResult.failure("Key cannot be null or empty");
        }
        UserQuery q;
        try {
            q = RequestFactory.buildUserQuery(QueryType.GET, key, "", directory == null ? "" : directory, false,
                    sessionManager.getToken());
        } catch (IllegalArgumentException e) {
            return QueryResult.failure(e.getMessage());
        }
        return sendQueryRequest(q, true);
    }

    public QueryResult create(String key, String value, boolean isEphemeral) {
        return create(key, value, "", isEphemeral);
    }

    public QueryResult create(String key, String value, String directory, boolean isEphemeral) {
        if (key == null || key.trim().isEmpty()) {
            return QueryResult.failure("Key cannot be null or empty");
        }
        UserQuery q;
        try {
            q = RequestFactory.buildUserQuery(QueryType.CREATE, key, value == null ? "" : value,
                    directory == null ? "" : directory, isEphemeral, sessionManager.getToken());
        } catch (IllegalArgumentException e) {
            return QueryResult.failure(e.getMessage());
        }
        return sendQueryRequest(q, false);
    }

    public QueryResult update(String key, String value) {
        return update(key, value, "");
    }

    public QueryResult update(String key, String value, String directory) {
        if (key == null || key.trim().isEmpty()) {
            return QueryResult.failure("Key cannot be null or empty");
        }
        UserQuery q;
        try {
            q = RequestFactory.buildUserQuery(QueryType.UPDATE, key, value == null ? "" : value,
                    directory == null ? "" : directory, false, sessionManager.getToken());
        } catch (IllegalArgumentException e) {
            return QueryResult.failure(e.getMessage());
        }
        return sendQueryRequest(q, false);
    }

    public QueryResult delete(String key) {
        return delete(key, "");
    }

    public QueryResult delete(String key, String directory) {
        if (key == null || key.trim().isEmpty()) {
            return QueryResult.failure("Key cannot be null or empty");
        }
        UserQuery q;
        try {
            q = RequestFactory.buildUserQuery(QueryType.DELETE, key, "",
                    directory == null ? "" : directory, false, sessionManager.getToken());
        } catch (IllegalArgumentException e) {
            return QueryResult.failure(e.getMessage());
        }
        return sendQueryRequest(q, false);
    }

    public void addWatch(String key, String directory) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }
        watchHandler.sendWatchRequest(key.trim(), directory == null ? "" : directory.trim(), null);
    }

    public void addWatch(String key, String directory, Watcher watcher) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }
        watchHandler.sendWatchRequest(key.trim(), directory == null ? "" : directory.trim(), watcher);
    }

    private PermissionsResult sendPermissionsRequest(UserPermissionsRequest request, boolean isReadOnly) {
        return sendRequest(request, MessageType.PERMISSIONS, isReadOnly, this::parsePermissionsResponse);
    }

    public PermissionsResult getUserPermissionsByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return PermissionsResult.failure("Email cannot be null or empty");
        }
        UserPermissionsRequest request = getUserPermissionsRequest(email.trim(), RequestType.GET,
                UserPermissions.newBuilder().build(), sessionManager.getToken().orElseGet(String::new));
        return sendPermissionsRequest(request, true);
    }

    private UserPermissionsRequest getUserPermissionsRequest(String email, RequestType requestType,
            UserPermissions userPermissions, String token) {
        return UserPermissionsRequest.newBuilder()
                .setRequestType(requestType)
                .setUserEmail(email == null ? "" : email.trim())
                .setToken(token)
                .setUserPermissions(userPermissions)
                .build();
    }

    public PermissionsResult setIsAdmin(String email, boolean isAdmin) {
        if (email == null || email.trim().isEmpty()) {
            return PermissionsResult.failure("Email cannot be null or empty");
        }
        UserPermissions userPerm = UserPermissions.newBuilder()
                .setIsAdmin(isAdmin)
                .build();
        UserPermissionsRequest request = getUserPermissionsRequest(email.trim(), RequestType.SET_IS_ADMIN,
                userPerm, sessionManager.getToken().orElseGet(String::new));
        return sendPermissionsRequest(request, false);
    }

    public PermissionsResult setCanCreateDirectories(String email, boolean canCreate) {
        if (email == null || email.trim().isEmpty()) {
            return PermissionsResult.failure("Email cannot be null or empty");
        }
        UserPermissions userPerm = UserPermissions.newBuilder()
                .setCanCreateDirectories(canCreate)
                .build();
        UserPermissionsRequest request = getUserPermissionsRequest(email.trim(), RequestType.SET_CAN_CREATE_DIRECTORIES,
                userPerm, sessionManager.getToken().orElseGet(String::new));
        return sendPermissionsRequest(request, false);
    }

    public PermissionsResult setDirectoryPermissions(String email, Map<String, Integer> directoryPermissions) {
        if (email == null || email.trim().isEmpty()) {
            return PermissionsResult.failure("Email cannot be null or empty");
        }
        if (directoryPermissions == null || directoryPermissions.isEmpty()) {
            return PermissionsResult.failure("Directory permissions cannot be null or empty");
        }
        UserPermissions.Builder permBuilder = UserPermissions.newBuilder();
        for (Map.Entry<String, Integer> entry : directoryPermissions.entrySet()) {
            String dir = entry.getKey();
            Integer mask = entry.getValue();
            if (dir == null || dir.trim().isEmpty()) {
                return PermissionsResult.failure("Directory name cannot be null or empty");
            }
            if (mask == null || !PermissionConstants.isValid(mask)) {
                return PermissionsResult.failure("Invalid permission mask: " + mask);
            }
            permBuilder.putDirectoryPermissions(dir.trim(), mask);
        }
        UserPermissionsRequest request = getUserPermissionsRequest(email.trim(), RequestType.SET_DIRECTORY_PERMISSIONS,
                permBuilder.build(), sessionManager.getToken().orElseGet(String::new));
        return sendPermissionsRequest(request, false);
    }

    public PermissionsResult setDirectoryPermission(String email, String directory, int permissionMask) {
        if (email == null || email.trim().isEmpty()) {
            return PermissionsResult.failure("Email cannot be null or empty");
        }
        if (directory == null || directory.trim().isEmpty()) {
            return PermissionsResult.failure("Directory name cannot be null or empty");
        }
        if (!PermissionConstants.isValid(permissionMask)) {
            return PermissionsResult.failure("Invalid permission mask: " + permissionMask);
        }
        return setDirectoryPermissions(email.trim(), java.util.Collections.singletonMap(directory.trim(), permissionMask));
    }

    private PermissionsResult parsePermissionsResponse(ByteString responseBytes) {
        if (responseBytes == null) {
            return PermissionsResult.failure("Transport failure or request rejected by cluster");
        }
        try {
            UserPermissionsResponse resp = UserPermissionsResponse.parseFrom(responseBytes.asReadOnlyByteBuffer());
            return new PermissionsResult(resp.getSuccess(),
                                         resp.getErrorMessage(),
                                         resp.hasUserPermissions() ? resp.getUserPermissions() : null);
        } catch (InvalidProtocolBufferException e) {
            LOG.error("Failed to parse permissions response", e);
            return PermissionsResult.failure("Invalid server response");
        }
    }
    public static class MetricsResult {
        private final boolean success;
        private final String errorMessage;
        private final long electionCount;
        private final long timeoutCount;
        private final long clientReadRequests;
        private final long clientWriteRequests;
        private final long numPendingRequestsInQueue;
        private final long numFailedClientReadOnServer;
        private final double appendEntryLatency;

        public MetricsResult(boolean success, String errorMessage, long electionCount, long timeoutCount,
                long clientReadRequests, long clientWriteRequests, long numPendingRequestsInQueue,
                long numFailedClientReadOnServer, double appendEntryLatency) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.electionCount = electionCount;
            this.timeoutCount = timeoutCount;
            this.clientReadRequests = clientReadRequests;
            this.clientWriteRequests = clientWriteRequests;
            this.numPendingRequestsInQueue = numPendingRequestsInQueue;
            this.numFailedClientReadOnServer = numFailedClientReadOnServer;
            this.appendEntryLatency = appendEntryLatency;
        }

        public static MetricsResult failure(String message) {
            return new MetricsResult(false, message, 0, 0, 0, 0, 0, 0, 0.0);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public long getElectionCount() {
            return electionCount;
        }

        public long getTimeoutCount() {
            return timeoutCount;
        }

        public long getClientReadRequests() {
            return clientReadRequests;
        }

        public long getClientWriteRequests() {
            return clientWriteRequests;
        }

        public long getNumPendingRequestsInQueue() {
            return numPendingRequestsInQueue;
        }

        public long getNumFailedClientReadOnServer() {
            return numFailedClientReadOnServer;
        }

        public double getAppendEntryLatency() {
            return appendEntryLatency;
        }

        @Override
        public String toString() {
            if (!success) {
                return String.format("MetricsResult{success=false, errorMessage='%s'}", errorMessage);
            }
            return "MetricsResult{" +
                    "success=" + success +
                    ", electionCount=" + electionCount +
                    ", timeoutCount=" + timeoutCount +
                    ", clientReadRequests=" + clientReadRequests +
                    ", clientWriteRequests=" + clientWriteRequests +
                    ", numPendingRequestsInQueue=" + numPendingRequestsInQueue +
                    ", numFailedClientReadOnServer=" + numFailedClientReadOnServer +
                    ", appendEntryLatency=" + appendEntryLatency +
                    '}';
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

    // TODO : refactor WatchHandler to its own file
    private class WatchHandler {

        public void sendWatchRequest(String key, String directory, Watcher customWatcher) {
            UserQuery query = RequestFactory.buildUserQuery(
                    QueryType.WATCH, key, "", directory,false, sessionManager.getToken());
            CompletableFuture<RaftClientReply> res = sendAsyncRequest(query, MessageType.QUERY, true);

            res.whenCompleteAsync((raftClientReply, throwable) -> {
                if (throwable != null) {
                    LOG.error("Watch request failed for key: {} dir: {}", key, directory, throwable);
                    return;
                }

                if (raftClientReply == null) {
                    LOG.error("Watch request returned null reply for key: {} dir: {}", key, directory);
                    return;
                }

                if (handleFailure(key, directory, raftClientReply)) return;

                ByteString content = raftClientReply.getMessage() == null ? null : raftClientReply.getMessage().getContent();
                WatcherResult watcherResult = parseWatcherResponse(content);
                if (!watcherResult.isSuccess()) {
                    LOG.error("Failed to set watch on key: {} in directory: {}. Error: {}",
                            key, directory, watcherResult.getErrorMessage());
                } else {
                    LOG.info("Successfully set watch on key: {} in directory: {}", key, directory);
                    try {
                        if (customWatcher != null) customWatcher.process(watcherResult.getWatchEvent());
                        else watcher.process(watcherResult.getWatchEvent());
                    } catch (Exception e) {
                        LOG.error("Watcher.process threw an exception for key: {} dir: {}", key, directory, e);
                    }
                }
            });
        }

        private boolean handleFailure(String key, String directory, RaftClientReply raftClientReply) {
            if (!raftClientReply.isSuccess()) {
                // Attempt to parse any error payload if present
                if (raftClientReply.getMessage() != null) {
                    WatcherResult watcherResult = parseWatcherResponse(raftClientReply.getMessage().getContent());
                    LOG.error("Failed to set watch on key: {} in directory: {}. Error: {}",
                            key, directory, watcherResult.getErrorMessage());
                } else {
                    LOG.error("Failed to set watch on key: {} in directory: {}. reply.isSuccess==false and no message present",
                            key, directory);
                }
                return true;
            }
            return false;
        }

        public WatcherResult parseWatcherResponse(ByteString responseBytes) {

            if (responseBytes == null) {
                return new WatcherResult(false, "null response", null);
            }

            QueryResponse response;
            try {
                response = QueryResponse.parseFrom(responseBytes.asReadOnlyByteBuffer());
            } catch (InvalidProtocolBufferException e) {
                LOG.error("Failed to parse watcher response", e);
                return new WatcherResult(false, "Invalid server response", null);
            }

            boolean success = response.getSuccess();
            String errorMessage = response.getErrorMessage();
            WatchEvent watchEvent = response.hasWatchEvents() ? response.getWatchEvents() : null;

            if (errorMessage.isEmpty()) {
                errorMessage = null;
            }

            return new WatcherResult(success, errorMessage, watchEvent);
        }

        private class WatcherResult {
            private final boolean success;
            private final String errorMessage;
            private final WatchEvent watchEvent;

            public WatcherResult(boolean success, String errorMessage, WatchEvent watchEvent) {
                this.success = success;
                this.errorMessage = errorMessage;
                this.watchEvent = watchEvent;
            }

            public boolean isSuccess() {
                return success;
            }

            public String getErrorMessage() {
                return errorMessage;
            }

            public WatchEvent getWatchEvent() {
                return watchEvent;
            }

            @Override
            public String toString() {
                return "WatcherResult{" +
                        "success=" + success +
                        ", errorMessage='" + errorMessage + '\'' +
                        ", watchEvent=" + watchEvent +
                        '}';
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                WatcherResult that = (WatcherResult) o;
                return success == that.success &&
                        Objects.equals(errorMessage, that.errorMessage) &&
                        Objects.equals(watchEvent, that.watchEvent);
            }

            @Override
            public int hashCode() {
                return Objects.hash(success, errorMessage, watchEvent);
            }
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
}
