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

import java.io.IOException;
import java.util.Optional;

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

    private MessageWrapper wrapAuthRequest(AuthRequest authRequest) {
        return MessageWrapper.newBuilder()
                .setType(MessageType.AUTH)
                .setPayload(authRequest.toByteString())
                .build();
    }

    private AuthenticationResult sendAuthRequest(AuthRequest authRequest, boolean isReadOnly) {
        try {
            MessageWrapper wrapper = wrapAuthRequest(authRequest);
            Message message = Message.valueOf(ByteString.copyFrom(wrapper.toByteArray()));

            RaftClientReply reply = isReadOnly
                    ? raftClient.io().sendReadOnly(message)
                    : raftClient.io().send(message);

            if (!reply.isSuccess()) {
                return AuthenticationResult.failure("Request failed: " + reply.getException());
            }

            return parseAuthResponse(reply.getMessage().getContent());

        } catch (IOException e) {
            LOG.error("Failed to send auth request", e);
            return AuthenticationResult.failure("Communication error: " + e.getMessage());
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

    private String sendMessage(String command, boolean expectBoolean) {
        try {
            RaftClientReply reply;
            if (command.startsWith("GET") || command.equals("READALL")) {
                reply = raftClient.io().sendReadOnly(Message.valueOf(command));
            } else {
                reply = raftClient.io().send(Message.valueOf(command));
            }

            if (!reply.isSuccess()) {
                return expectBoolean ? null : "ERROR";
            }

            String content = reply.getMessage().getContent().toStringUtf8();
            return expectBoolean ? content : content;
        } catch (Exception e) {
            return expectBoolean ? null : "ERROR";
        }
    }

    public String readAll() {
        return sendMessage("READALL", false);
    }

    public String read(String key) {
        return sendMessage("GET " + key, false);
    }

    public String read(String key, String directory) {
        return sendMessage("GET " + key + " IN " + directory, false);
    }

    public String write(String key, String value) {
        return sendMessage("PUT " + key + "=" + value, false);
    }

    public String write(String key, String value, String directory) {
        return sendMessage("PUT " + key + "=" + value + " IN " + directory, false);
    }

    public boolean delete(String key) {
        String result = sendMessage("DELETE " + key, false);
        return "OK ENTRY DELETED".equals(result);
    }

    public boolean delete(String key, String directory) {
        String result = sendMessage("DELETE " + key + " IN " + directory, false);
        return "OK ENTRY DELETED".equals(result);
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
}
