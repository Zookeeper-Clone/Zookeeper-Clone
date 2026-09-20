import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import client.zookeeper.PermissionConstants;
import client.zookeeper.ZookeeperClient;
import org.apache.ratis.client.RaftClient;
import org.apache.ratis.client.api.BlockingApi;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.protocol.RaftClientReply;
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.zookeeper.proto.auth.AuthResponse;
import server.zookeeper.proto.permissions.UserPermissions;
import server.zookeeper.proto.permissions.UserPermissionsResponse;
import server.zookeeper.proto.query.QueryResponse;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ZookeeperClientSecurityAndValidationTest {

    private RaftClient mockRaftClient;
    private BlockingApi mockIo;
    private ZookeeperClient client;

    @BeforeEach
    void setUp() {
        mockRaftClient = mock(RaftClient.class);
        mockIo = mock(BlockingApi.class);
        when(mockRaftClient.io()).thenReturn(mockIo);
        client = new ZookeeperClient(mockRaftClient, event -> {});
    }

    // ==================== CREDENTIAL & INPUT VALIDATION ====================

    @Test
    void testRegisterWithNullOrEmptyCredentials() {
        assertFalse(client.register(null, "password").isSuccess());
        assertFalse(client.register("", "password").isSuccess());
        assertFalse(client.register("   ", "password").isSuccess());
        assertFalse(client.register("user@test.com", null).isSuccess());
        assertFalse(client.register("user@test.com", "").isSuccess());

        verifyNoInteractions(mockIo);
    }

    @Test
    void testLoginWithNullOrEmptyCredentials() {
        assertFalse(client.login(null, "password").isSuccess());
        assertFalse(client.login("", "password").isSuccess());
        assertFalse(client.login("   ", "password").isSuccess());
        assertFalse(client.login("user@test.com", null).isSuccess());
        assertFalse(client.login("user@test.com", "").isSuccess());

        verifyNoInteractions(mockIo);
    }

    @Test
    void testOAuthWithNullOrEmptyInputs() {
        assertFalse(client.registerOAuth(null, "token").isSuccess());
        assertFalse(client.registerOAuth("", "token").isSuccess());
        assertFalse(client.registerOAuth("user@test.com", null).isSuccess());
        assertFalse(client.registerOAuth("user@test.com", "").isSuccess());

        assertFalse(client.loginOAuth(null, "token").isSuccess());
        assertFalse(client.loginOAuth("", "token").isSuccess());
        assertFalse(client.loginOAuth("user@test.com", null).isSuccess());
        assertFalse(client.loginOAuth("user@test.com", "").isSuccess());

        verifyNoInteractions(mockIo);
    }

    @Test
    void testSetSessionTokenValidation() {
        assertThrows(IllegalArgumentException.class, () -> client.setSessionToken(null));
        assertThrows(IllegalArgumentException.class, () -> client.setSessionToken(""));
        assertThrows(IllegalArgumentException.class, () -> client.setSessionToken("   "));

        client.setSessionToken("valid-token-123");
        assertTrue(client.isAuthenticated());
        assertEquals("valid-token-123", client.getSessionToken().orElse(""));
    }

    // ==================== SESSION & IDENTITY LIFECYCLE ====================

    @Test
    void testLoginSuccessEstablishesSessionAndCurrentUser() throws IOException {
        AuthResponse response = AuthResponse.newBuilder()
                .setSuccess(true)
                .setSessionToken("session-token-456")
                .build();

        Message replyMessage = mock(Message.class);
        when(replyMessage.getContent()).thenReturn(ByteString.copyFrom(response.toByteArray()));

        RaftClientReply reply = mock(RaftClientReply.class);
        when(reply.isSuccess()).thenReturn(true);
        when(reply.getMessage()).thenReturn(replyMessage);

        when(mockIo.send(any(Message.class))).thenReturn(reply);

        ZookeeperClient.AuthenticationResult result = client.login("alice@example.com", "secret123");

        assertTrue(result.isSuccess());
        assertEquals("Success", result.getMessage());
        assertTrue(client.isAuthenticated());
        assertEquals("session-token-456", client.getSessionToken().orElse(""));
        assertEquals("alice@example.com", client.getCurrentUserEmail().orElse(""));
    }

    @Test
    void testLoginSuccessMissingSessionTokenFails() throws IOException {
        AuthResponse response = AuthResponse.newBuilder()
                .setSuccess(true)
                .setSessionToken("") // Empty token
                .build();

        Message replyMessage = mock(Message.class);
        when(replyMessage.getContent()).thenReturn(ByteString.copyFrom(response.toByteArray()));

        RaftClientReply reply = mock(RaftClientReply.class);
        when(reply.isSuccess()).thenReturn(true);
        when(reply.getMessage()).thenReturn(replyMessage);

        when(mockIo.send(any(Message.class))).thenReturn(reply);

        ZookeeperClient.AuthenticationResult result = client.login("alice@example.com", "secret123");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("missing session token"));
        assertFalse(client.isAuthenticated());
    }

    @Test
    void testLogoutWithoutActiveSession() {
        ZookeeperClient.AuthenticationResult result = client.logout();
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("No active session"));
    }

    @Test
    void testLogoutWithActiveSession() throws IOException {
        // First set a token
        client.setSessionToken("test-session");

        AuthResponse response = AuthResponse.newBuilder()
                .setSuccess(true)
                .build();

        Message replyMessage = mock(Message.class);
        when(replyMessage.getContent()).thenReturn(ByteString.copyFrom(response.toByteArray()));

        RaftClientReply reply = mock(RaftClientReply.class);
        when(reply.isSuccess()).thenReturn(true);
        when(reply.getMessage()).thenReturn(replyMessage);

        when(mockIo.send(any(Message.class))).thenReturn(reply);

        ZookeeperClient.AuthenticationResult result = client.logout();
        assertTrue(result.isSuccess());
        assertFalse(client.isAuthenticated());
        assertTrue(client.getSessionToken().isEmpty());
        assertTrue(client.getCurrentUserEmail().isEmpty());
    }

    // ==================== PERMISSIONS VALIDATION ====================

    @Test
    void testPermissionConstantsBitmaskValidation() {
        assertTrue(PermissionConstants.isValid(0));
        assertTrue(PermissionConstants.isValid(PermissionConstants.CREATE));
        assertTrue(PermissionConstants.isValid(PermissionConstants.READ));
        assertTrue(PermissionConstants.isValid(PermissionConstants.UPDATE));
        assertTrue(PermissionConstants.isValid(PermissionConstants.DELETE));
        assertTrue(PermissionConstants.isValid(PermissionConstants.READ_ONLY));
        assertTrue(PermissionConstants.isValid(PermissionConstants.MODIFY_ONLY));
        assertTrue(PermissionConstants.isValid(PermissionConstants.CREATE_AND_READ));
        assertTrue(PermissionConstants.isValid(PermissionConstants.READ_WRITE));
        assertTrue(PermissionConstants.isValid(PermissionConstants.FULL_ACCESS));

        // Invalid bitmasks
        assertFalse(PermissionConstants.isValid(-1));
        assertFalse(PermissionConstants.isValid(16));
        assertFalse(PermissionConstants.isValid(32));
        assertFalse(PermissionConstants.isValid(100));

        // hasPermission edge cases
        assertTrue(PermissionConstants.hasPermission(PermissionConstants.FULL_ACCESS, PermissionConstants.READ));
        assertFalse(PermissionConstants.hasPermission(16, PermissionConstants.READ)); // invalid mask rejected
        assertFalse(PermissionConstants.hasPermission(PermissionConstants.FULL_ACCESS, 0)); // NONE is false
        assertFalse(PermissionConstants.hasPermission(PermissionConstants.FULL_ACCESS, 16)); // invalid req rejected
    }

    @Test
    void testSetDirectoryPermissionValidation() {
        assertFalse(client.setDirectoryPermission(null, "dir", 1).isSuccess());
        assertFalse(client.setDirectoryPermission("", "dir", 1).isSuccess());
        assertFalse(client.setDirectoryPermission("a@b.com", null, 1).isSuccess());
        assertFalse(client.setDirectoryPermission("a@b.com", "", 1).isSuccess());
        assertFalse(client.setDirectoryPermission("a@b.com", "dir", -1).isSuccess());
        assertFalse(client.setDirectoryPermission("a@b.com", "dir", 16).isSuccess());
        assertFalse(client.setDirectoryPermission("a@b.com", "dir", 32).isSuccess());

        verifyNoInteractions(mockIo);
    }

    @Test
    void testSetDirectoryPermissionsMapValidation() {
        assertFalse(client.setDirectoryPermissions(null, Collections.singletonMap("dir", 1)).isSuccess());
        assertFalse(client.setDirectoryPermissions("a@b.com", null).isSuccess());
        assertFalse(client.setDirectoryPermissions("a@b.com", Collections.emptyMap()).isSuccess());

        Map<String, Integer> invalidMaskMap = new HashMap<>();
        invalidMaskMap.put("dir", 99);
        assertFalse(client.setDirectoryPermissions("a@b.com", invalidMaskMap).isSuccess());

        Map<String, Integer> emptyDirMap = new HashMap<>();
        emptyDirMap.put("", 1);
        assertFalse(client.setDirectoryPermissions("a@b.com", emptyDirMap).isSuccess());

        verifyNoInteractions(mockIo);
    }

    @Test
    void testUserPermissionsEmailValidation() {
        assertFalse(client.getUserPermissionsByEmail(null).isSuccess());
        assertFalse(client.getUserPermissionsByEmail("").isSuccess());
        assertFalse(client.setIsAdmin(null, true).isSuccess());
        assertFalse(client.setIsAdmin("", true).isSuccess());
        assertFalse(client.setCanCreateDirectories(null, true).isSuccess());
        assertFalse(client.setCanCreateDirectories("", true).isSuccess());

        verifyNoInteractions(mockIo);
    }

    // ==================== CRUD & WATCH INPUT VALIDATION ====================

    @Test
    void testCRUDInputValidation() {
        assertFalse(client.create(null, "val", false).isSuccess());
        assertFalse(client.create("", "val", false).isSuccess());
        assertFalse(client.read(null).isSuccess());
        assertFalse(client.read("").isSuccess());
        assertFalse(client.update(null, "val").isSuccess());
        assertFalse(client.update("", "val").isSuccess());
        assertFalse(client.delete(null).isSuccess());
        assertFalse(client.delete("").isSuccess());

        assertThrows(IllegalArgumentException.class, () -> client.addWatch(null, "dir"));
        assertThrows(IllegalArgumentException.class, () -> client.addWatch("", "dir"));

        verifyNoInteractions(mockIo);
    }

    // ==================== TRANSPORT & FAILURE HANDLING ====================

    @Test
    void testRaftReplyNotSuccessDoesNotThrowNPE() throws IOException {
        RaftClientReply failureReply = mock(RaftClientReply.class);
        when(failureReply.isSuccess()).thenReturn(false);

        when(mockIo.send(any(Message.class))).thenReturn(failureReply);
        when(mockIo.sendReadOnly(any(Message.class))).thenReturn(failureReply);

        // Auth request
        ZookeeperClient.AuthenticationResult authRes = client.login("a@b.com", "pass");
        assertFalse(authRes.isSuccess());
        assertTrue(authRes.getMessage().contains("Transport failure"));

        // Query read
        ZookeeperClient.QueryResult readRes = client.read("key1");
        assertFalse(readRes.isSuccess());
        assertTrue(readRes.getMessage().contains("Transport failure"));

        // Query write
        ZookeeperClient.QueryResult writeRes = client.create("key1", "val1", false);
        assertFalse(writeRes.isSuccess());
        assertTrue(writeRes.getMessage().contains("Transport failure"));

        // Permissions
        ZookeeperClient.PermissionsResult permRes = client.getUserPermissionsByEmail("a@b.com");
        assertFalse(permRes.isSuccess());
        assertTrue(permRes.getMessage().contains("Transport failure"));

        // Metrics
        ZookeeperClient.MetricsResult metricsRes = client.getMetrics();
        assertFalse(metricsRes.isSuccess());
        assertTrue(metricsRes.getErrorMessage().contains("Transport failure"));
    }

    @Test
    void testIOExceptionDoesNotThrowException() throws IOException {
        when(mockIo.send(any(Message.class))).thenThrow(new IOException("Cluster unreachable"));
        when(mockIo.sendReadOnly(any(Message.class))).thenThrow(new IOException("Cluster unreachable"));

        ZookeeperClient.AuthenticationResult authRes = client.login("a@b.com", "pass");
        assertFalse(authRes.isSuccess());

        ZookeeperClient.QueryResult readRes = client.read("key1");
        assertFalse(readRes.isSuccess());

        ZookeeperClient.QueryResult writeRes = client.create("key1", "val1", false);
        assertFalse(writeRes.isSuccess());

        ZookeeperClient.PermissionsResult permRes = client.getUserPermissionsByEmail("a@b.com");
        assertFalse(permRes.isSuccess());
    }

    @Test
    void testMalformedProtobufResponseHandling() throws IOException {
        Message replyMessage = mock(Message.class);
        when(replyMessage.getContent()).thenReturn(ByteString.copyFromUtf8("Not-A-Valid-Protobuf"));

        RaftClientReply reply = mock(RaftClientReply.class);
        when(reply.isSuccess()).thenReturn(true);
        when(reply.getMessage()).thenReturn(replyMessage);

        when(mockIo.send(any(Message.class))).thenReturn(reply);
        when(mockIo.sendReadOnly(any(Message.class))).thenReturn(reply);

        ZookeeperClient.AuthenticationResult authRes = client.login("a@b.com", "pass");
        assertFalse(authRes.isSuccess());
        assertTrue(authRes.getMessage().toLowerCase().contains("invalid"));

        ZookeeperClient.QueryResult queryRes = client.read("key1");
        assertFalse(queryRes.isSuccess());
        assertTrue(queryRes.getMessage().toLowerCase().contains("invalid"));

        ZookeeperClient.PermissionsResult permRes = client.getUserPermissionsByEmail("a@b.com");
        assertFalse(permRes.isSuccess());
        assertTrue(permRes.getMessage().toLowerCase().contains("invalid"));
    }
}
