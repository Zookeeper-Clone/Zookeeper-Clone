package client.zookeeper;


import server.zookeeper.proto.auth.AuthRequest;
import server.zookeeper.proto.auth.AuthOperationType;
import server.zookeeper.proto.query.QueryType;
import server.zookeeper.proto.query.UserQuery;
import server.zookeeper.proto.MessageType;
import server.zookeeper.proto.MessageWrapper;

import java.util.Optional;

public class RequestFactory {

    public static AuthRequest buildAuthRequest(AuthOperationType operation, String email, String password, Optional<String> token) {
        AuthRequest.Builder builder = AuthRequest.newBuilder()
                .setOperation(operation)
                .setEmail(email)
                .setPassword(password);
        token.ifPresent(builder::setSessionToken);
        return builder.build();
    }

    public static AuthRequest buildOAuthRequest(AuthOperationType operation, String email, String oauthToken, Optional<String> token) {
        AuthRequest.Builder builder = AuthRequest.newBuilder()
                .setOperation(operation)
                .setEmail(email)
                .setGoogleToken(oauthToken);
        token.ifPresent(builder::setSessionToken);
        return builder.build();
    }

    public static AuthRequest buildHeartbeatRequest(String token) {
        return AuthRequest.newBuilder()
                .setOperation(AuthOperationType.HEARTBEAT)
                .setSessionToken(token)
                .build();
    }

    public static AuthRequest buildLogoutRequest(String token) {
        return AuthRequest.newBuilder()
                .setOperation(AuthOperationType.LOGOUT)
                .setSessionToken(token)
                .build();
    }

    public static UserQuery buildUserQuery(QueryType type, String key, String value, String directory, Optional<String> token) {
        UserQuery.Builder builder = UserQuery.newBuilder()
                .setQueryType(type)
                .setKey(key)
                .setValue(value)
                .setDirectory(directory);
        token.ifPresent(builder::setSessionToken);

        return builder.build();
    }

    public static MessageWrapper wrapMessage(MessageType type, com.google.protobuf.Message payload, Optional<String> token) {
        MessageWrapper.Builder builder = MessageWrapper.newBuilder()
                .setType(type)
                .setPayload(payload.toByteString());
        token.ifPresent(builder::setSessionToken);

        return builder.build();
    }
}
