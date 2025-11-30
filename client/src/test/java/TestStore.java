import static org.junit.jupiter.api.Assertions.*;

import client.zookeeper.ZookeeperClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import server.zookeeper.proto.auth.AuthOperationType;
import server.zookeeper.proto.auth.AuthRequest;
import server.zookeeper.proto.query.QueryType;
import server.zookeeper.proto.query.UserQuery;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

public class TestStore {

    private ZookeeperClient client;

    @BeforeEach
    void setUp() {
        // Do not initialize RaftClient; focus on logic-only tests
        client = new ZookeeperClient(null);
    }

    // ===================== SESSION TOKEN TESTS =====================

    @Test
    void testIsAuthenticatedWithoutLogin() {
        assertFalse(client.isAuthenticated());
        assertFalse(client.getSessionToken().isPresent());
    }


    // ===================== AUTHENTICATION RESULT TESTS =====================

    @Test
    void testAuthenticationResultSuccessWithToken() {
        ZookeeperClient.AuthenticationResult result =
                ZookeeperClient.AuthenticationResult.success("ok", "token123");

        assertTrue(result.isSuccess());
        assertEquals("ok", result.getMessage());
        assertEquals(Optional.of("token123"), result.getSessionToken());
    }

    @Test
    void testAuthenticationResultSuccessWithoutToken() {
        ZookeeperClient.AuthenticationResult result =
                ZookeeperClient.AuthenticationResult.success("ok");

        assertTrue(result.isSuccess());
        assertEquals("ok", result.getMessage());
        assertEquals(Optional.empty(), result.getSessionToken());
    }

    @Test
    void testAuthenticationResultFailure() {
        ZookeeperClient.AuthenticationResult result =
                ZookeeperClient.AuthenticationResult.failure("fail");

        assertFalse(result.isSuccess());
        assertEquals("fail", result.getMessage());
        assertEquals(Optional.empty(), result.getSessionToken());
    }

    // ===================== QUERY RESULT TESTS =====================

    @Test
    void testQueryResultSuccessWithValue() {
        ZookeeperClient.QueryResult result =
                ZookeeperClient.QueryResult.success("ok", "val");

        assertTrue(result.isSuccess());
        assertEquals("ok", result.getMessage());
        assertEquals("val", result.getValue());
    }

    @Test
    void testQueryResultSuccessWithoutValue() {
        ZookeeperClient.QueryResult result =
                ZookeeperClient.QueryResult.success("ok");

        assertTrue(result.isSuccess());
        assertEquals("ok", result.getMessage());
        assertNull(result.getValue());
    }

    @Test
    void testQueryResultFailure() {
        ZookeeperClient.QueryResult result =
                ZookeeperClient.QueryResult.failure("fail");

        assertFalse(result.isSuccess());
        assertEquals("fail", result.getMessage());
        assertNull(result.getValue());
    }

    // ===================== TO STRING TESTS =====================

    @Test
    void testAuthenticationResultToString() {
        ZookeeperClient.AuthenticationResult result =
                ZookeeperClient.AuthenticationResult.success("ok", "tok");
        String str = result.toString();
        assertTrue(str.contains("success=true"));
        assertTrue(str.contains("hasToken=true"));
    }

    @Test
    void testQueryResultToString() {
        ZookeeperClient.QueryResult result =
                ZookeeperClient.QueryResult.success("ok", "val");
        String str = result.toString();
        assertTrue(str.contains("success=true"));
        assertTrue(str.contains("value=val"));
    }

    @Test
    void testBuildQueryRequest() throws Exception {
        Method method = ZookeeperClient.class.getDeclaredMethod(
                "buildQueryRequest", QueryType.class, String.class, String.class, String.class);
        method.setAccessible(true);

        UserQuery query = (UserQuery) method.invoke(client, QueryType.GET, "key1", "val1", "dir1");

        assertEquals(QueryType.GET, query.getQueryType());
        assertEquals("key1", query.getKey());
        assertEquals("val1", query.getValue());
        assertEquals("dir1", query.getDirectory());
    }

    @Test
    void testBuildAuthRequestWithoutSessionToken() throws Exception {
        Method method = ZookeeperClient.class.getDeclaredMethod(
                "buildAuthRequest", AuthOperationType.class, String.class, String.class);
        method.setAccessible(true);

        AuthRequest req = (AuthRequest) method.invoke(client, AuthOperationType.REGISTER, "a@b.com", "pass");

        assertEquals(AuthOperationType.REGISTER, req.getOperation());
        assertEquals("a@b.com", req.getEmail());
        assertEquals("pass", req.getPassword());
    }

    @Test
    void testBuildAuthRequestWithSessionToken() throws Exception {
        // Manually set sessionToken via reflection
        var tokenField = ZookeeperClient.class.getDeclaredField("sessionToken");
        tokenField.setAccessible(true);
        tokenField.set(client, "tok123");

        Method method = ZookeeperClient.class.getDeclaredMethod(
                "buildAuthRequest", AuthOperationType.class, String.class, String.class);
        method.setAccessible(true);

        AuthRequest req = (AuthRequest) method.invoke(client, AuthOperationType.LOGIN, "b@c.com", "pass2");

        assertEquals(AuthOperationType.LOGIN, req.getOperation());
        assertEquals("b@c.com", req.getEmail());
        assertEquals("pass2", req.getPassword());
        assertEquals("tok123", req.getSessionToken());
    }

    @Test
    void testBuildOAuthRequestWithoutSessionToken() throws Exception {
        Method method = ZookeeperClient.class.getDeclaredMethod(
                "buildOAuthRequest", AuthOperationType.class, String.class, String.class);
        method.setAccessible(true);

        AuthRequest req = (AuthRequest) method.invoke(client, AuthOperationType.LOGIN_OAUTH, "o@auth.com", "tokenX");

        assertEquals(AuthOperationType.LOGIN_OAUTH, req.getOperation());
        assertEquals("o@auth.com", req.getEmail());
        assertEquals("tokenX", req.getGoogleToken());
        assertTrue(req.getSessionToken().isEmpty()); // instead of hasSessionToken()
    }

    @Test
    void testBuildOAuthRequestWithSessionToken() throws Exception {
        // Manually set sessionToken via reflection
        var tokenField = ZookeeperClient.class.getDeclaredField("sessionToken");
        tokenField.setAccessible(true);
        tokenField.set(client, "sessTok");

        Method method = ZookeeperClient.class.getDeclaredMethod(
                "buildOAuthRequest", AuthOperationType.class, String.class, String.class);
        method.setAccessible(true);

        AuthRequest req = (AuthRequest) method.invoke(client, AuthOperationType.REGISTER_OAUTH, "x@y.com", "oauthTok");

        assertEquals(AuthOperationType.REGISTER_OAUTH, req.getOperation());
        assertEquals("x@y.com", req.getEmail());
        assertEquals("oauthTok", req.getGoogleToken());
        assertEquals("sessTok", req.getSessionToken());
    }
}
