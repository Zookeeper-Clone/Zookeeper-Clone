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
import server.zookeeper.proto.query.QueryResponse;
import server.zookeeper.proto.query.QueryType;
import server.zookeeper.proto.query.UserQuery;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;

public class ZookeeperClient {
    private static final Logger LOG = LoggerFactory.getLogger(ZookeeperClient.class);

    private final RaftClient raftClient;
    private String sessionToken;

    public ZookeeperClient(RaftClient raftClient) {
        this.raftClient = raftClient;
        this.sessionToken = null;
    }

    public AuthenticationResult register(String email, String password) {
        AuthRequest authRequest = buildAuthRequest(AuthOperationType.REGISTER, email, password);
        return sendAuthRequest(authRequest, false);
    }

    public AuthenticationResult login(String email, String password) {
        AuthRequest authRequest = buildAuthRequest(AuthOperationType.LOGIN, email, password);
        AuthenticationResult result = sendAuthRequest(authRequest, true);

        if (result.isSuccess() && result.getSessionToken().isPresent()) {
            this.sessionToken = result.getSessionToken().get();
            LOG.info("User {} logged in successfully", email);
        }
        return result;
    }

    public AuthenticationResult registerOAuth(String email, String OAuthToken) {
        AuthRequest authRequest = buildOAuthRequest(AuthOperationType.REGISTER_OAUTH, email, OAuthToken);
        return sendAuthRequest(authRequest, false);
    }

    public AuthenticationResult loginOAuth(String email, String OAuthToken) {
        AuthRequest authRequest = buildOAuthRequest(AuthOperationType.LOGIN_OAUTH, email, OAuthToken);
        AuthenticationResult result = sendAuthRequest(authRequest, true);
        if (result.isSuccess() && result.getSessionToken().isPresent()) {
            this.sessionToken = result.getSessionToken().get();
            LOG.info("User {} logged in successfully", email);
        }
        return result;
    }

    public boolean isAuthenticated() {
        return sessionToken != null && !sessionToken.isEmpty();
    }

    public Optional<String> getSessionToken() {
        return Optional.ofNullable(sessionToken);
    }
    private UserQuery buildQueryRequest(QueryType queryType, String key , String value,String directory){
        return UserQuery.newBuilder()
                .setQueryType(queryType)
                .setKey(key)
                .setValue(value)
                .setDirectory(directory)
                .build();
    }
    private AuthRequest buildAuthRequest(AuthOperationType operation, String email, String password) {
        AuthRequest.Builder builder = AuthRequest.newBuilder()
                .setOperation(operation)
                .setEmail(email)
                .setPassword(password);

        if (sessionToken != null) {
            builder.setSessionToken(sessionToken);
        }

        return builder.build();
    }

    private AuthRequest buildOAuthRequest(AuthOperationType operation, String email , String OAuthToken){
        AuthRequest.Builder builder = AuthRequest.newBuilder()
                .setOperation(operation)
                .setEmail(email)
                .setGoogleToken(OAuthToken);
        if (sessionToken != null){
            builder.setSessionToken(sessionToken);
        }
        return builder.build();
    }

    private AuthenticationResult sendAuthRequest(AuthRequest authRequest, boolean isReadOnly) {
        return sendRequest(authRequest , MessageType.AUTH , isReadOnly, this::parseAuthResponse);
    }
    private QueryResult sendQueryRequest(UserQuery userQyery, boolean isReadOnly){
        return sendRequest(userQyery, MessageType.QUERY, isReadOnly,this::parseQueryResponse);
    }
    private <T> T sendRequest(com.google.protobuf.Message request, MessageType type, boolean isReadOnly, Function<ByteString, T> responseParser){
        try {
            MessageWrapper wrapper = MessageWrapper.newBuilder()
                    .setType(type)
                    .setPayload(request.toByteString())
                    .build();
            Message message = Message.valueOf(ByteString.copyFrom(wrapper.toByteArray()));

            RaftClientReply reply = isReadOnly
                    ? raftClient.io().sendReadOnly(message)
                    : raftClient.io().send(message);
            if (!reply.isSuccess()){
                return responseParser.apply(null);
            }
            return responseParser.apply(reply.getMessage().getContent());
        }catch (IOException e){
            LOG.error("Request failed" , e);
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
    private QueryResult parseQueryResponse(ByteString responseBytes){
        try {
            QueryResponse queryResponse = QueryResponse.parseFrom(responseBytes.asReadOnlyByteBuffer());

            return new QueryResult(
                    queryResponse.getSuccess(),
                    queryResponse.getErrorMessage(),
                    queryResponse.getValue());
        }catch (InvalidProtocolBufferException e){
            LOG.error("Failed to parse query response");
            return QueryResult.failure("Invalid Server response");
        }
    }


    public QueryResult read(String key) {
        UserQuery q = buildQueryRequest(QueryType.GET,key,"","");
        return sendQueryRequest(q,true);
    }

    public QueryResult read(String key, String directory) {
        UserQuery q = buildQueryRequest(QueryType.GET,key,"",directory);
        return sendQueryRequest(q,true);
    }

    public QueryResult write(String key, String value) {
        UserQuery q = buildQueryRequest(QueryType.WRITE,key,value,"");
        return sendQueryRequest(q,false);
    }

    public QueryResult write(String key, String value,String directory) {
        UserQuery q = buildQueryRequest(QueryType.WRITE,key,value,directory);
        return sendQueryRequest(q,false);
    }

    public QueryResult delete(String key) {
        UserQuery q = buildQueryRequest(QueryType.DELETE, key, "" , "");
        return sendQueryRequest(q,false);
    }

    public QueryResult delete(String key, String directory) {
        UserQuery q = buildQueryRequest(QueryType.DELETE, key, "" , directory);
        return sendQueryRequest(q,false);
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

        public String getValue() {return value;}

        @Override
        public String toString() {
            return String.format("QueryResult{success=%s, message='%s', value=%s}",
                    success, message, value);
        }
    }
}
