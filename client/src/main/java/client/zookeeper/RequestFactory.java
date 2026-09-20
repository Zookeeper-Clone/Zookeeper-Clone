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
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        AuthRequest.Builder builder = AuthRequest.newBuilder()
                .setOperation(operation)
                .setEmail(email.trim())
                .setPassword(password);
        if (token != null) {
            token.ifPresent(t -> {
                if (t != null && !t.trim().isEmpty()) {
                    builder.setSessionToken(t.trim());
                }
            });
        }
        return builder.build();
    }

    public static AuthRequest buildOAuthRequest(AuthOperationType operation, String email, String oauthToken, Optional<String> token) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }
        if (oauthToken == null || oauthToken.trim().isEmpty()) {
            throw new IllegalArgumentException("OAuth token cannot be null or empty");
        }
        AuthRequest.Builder builder = AuthRequest.newBuilder()
                .setOperation(operation)
                .setEmail(email.trim())
                .setGoogleToken(oauthToken.trim());
        if (token != null) {
            token.ifPresent(t -> {
                if (t != null && !t.trim().isEmpty()) {
                    builder.setSessionToken(t.trim());
                }
            });
        }
        return builder.build();
    }

    public static AuthRequest buildHeartbeatRequest(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Session token cannot be null or empty");
        }
        return AuthRequest.newBuilder()
                .setOperation(AuthOperationType.HEARTBEAT)
                .setSessionToken(token.trim())
                .build();
    }

    public static AuthRequest buildLogoutRequest(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("Session token cannot be null or empty");
        }
        return AuthRequest.newBuilder()
                .setOperation(AuthOperationType.LOGOUT)
                .setSessionToken(token.trim())
                .build();
    }

    public static UserQuery buildUserQuery(QueryType type, String key, String value, String directory, boolean isEphemeral, Optional<String> token) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }
        UserQuery.Builder builder = UserQuery.newBuilder()
                .setQueryType(type)
                .setKey(key.trim())
                .setValue(value == null ? "" : value)
                .setDirectory(directory == null ? "" : directory.trim())
                .setIsEphemeral(isEphemeral);
        if (token != null) {
            token.ifPresent(t -> {
                if (t != null && !t.trim().isEmpty()) {
                    builder.setSessionToken(t.trim());
                }
            });
        }

        return builder.build();
    }

    public static MessageWrapper wrapMessage(MessageType type, com.google.protobuf.Message payload, Optional<String> token) {
        if (type == null) {
            throw new IllegalArgumentException("MessageType cannot be null");
        }
        if (payload == null) {
            throw new IllegalArgumentException("Payload cannot be null");
        }
        MessageWrapper.Builder builder = MessageWrapper.newBuilder()
                .setType(type)
                .setPayload(payload.toByteString());
        if (token != null) {
            token.ifPresent(t -> {
                if (t != null && !t.trim().isEmpty()) {
                    builder.setSessionToken(t.trim());
                }
            });
        }

        return builder.build();
    }
}
