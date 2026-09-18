package server.zookeeper.modules;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.ratis.protocol.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import server.zookeeper.DB.AuthRepository;
import server.zookeeper.DB.DataBase;
import server.zookeeper.DB.SessionRepository;
import server.zookeeper.proto.auth.UserAuth;
import server.zookeeper.proto.permissions.RequestType;
import server.zookeeper.proto.permissions.UserPermissions;
import server.zookeeper.proto.permissions.UserPermissionsRequest;
import server.zookeeper.proto.permissions.UserPermissionsResponse;
import server.zookeeper.proto.query.QueryResponse;
import server.zookeeper.proto.query.QueryType;
import server.zookeeper.proto.query.UserQuery;
import server.zookeeper.proto.session.Session;
import server.zookeeper.util.PermissionConstants;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test cases for the authorization system.
 * Tests cover various permission scenarios including:
 * - Admin users bypassing permissions
 * - Directory-specific permissions
 * - Permission bitmask checks (CREATE, READ, UPDATE, DELETE)
 * - Missing/invalid session tokens
 * - Unauthorized and forbidden responses
 */
public class AuthorizationTest {

    private DataBase mockDb;
    private SessionManager mockSessionManager;
    private SessionRepository mockSessionRepository;
    private AuthRepository mockAuthRepository;
    private AuthzHandler authzHandler;
    private QueryHandler queryHandler;

    private static final String VALID_TOKEN = "valid-session-token-123";
    private static final String INVALID_TOKEN = "invalid-token";
    private static final String TEST_USER_EMAIL = "testuser@example.com";
    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String TEST_DIRECTORY = "test-directory";

    @BeforeEach
    public void setUp() {
        mockDb = mock(DataBase.class);
        mockSessionManager = mock(SessionManager.class);
        mockSessionRepository = mock(SessionRepository.class);
        mockAuthRepository = mock(AuthRepository.class);

        authzHandler = new AuthzHandler(mockSessionRepository, mockAuthRepository);
        queryHandler = new QueryHandler(mockDb, mockSessionManager, authzHandler);
    }

    private Session createSession(String token, String email) {
        return Session.newBuilder()
                .setSessionToken(token)
                .setUserEmail(email)
                .setIsValid(true)
                .build();
    }

    private UserAuth createUser(String email, boolean isAdmin, Map<String, Integer> permissions) {
        UserAuth.Builder builder = UserAuth.newBuilder()
                .setEmail(email)
                .setIsAdmin(isAdmin);
        if (permissions != null) {
            builder.putAllPermissions(permissions);
        }
        return builder.build();
    }

    private UserQuery createQuery(QueryType type, String key, String value, String directory, String token) {
        UserQuery.Builder builder = UserQuery.newBuilder()
                .setQueryType(type)
                .setKey(key)
                .setValue(value)
                .setSessionToken(token);
        if (directory != null) {
            builder.setDirectory(directory);
        }
        return builder.build();
    }

    private QueryResponse executeQuery(UserQuery query) throws ExecutionException, InterruptedException, InvalidProtocolBufferException {
        CompletableFuture<Message> future = queryHandler.handle(query.toByteArray(), true);
        return QueryResponse.parseFrom(future.get().getContent().toByteArray());
    }

    // ==================== Test Cases ====================

    @Test
    @DisplayName("1. Admin user should have access to all operations")
    public void testAdminHasFullAccess() {
        // Setup: Admin user with valid session
        when(mockSessionRepository.getSession(VALID_TOKEN))
                .thenReturn(Optional.of(createSession(VALID_TOKEN, ADMIN_EMAIL)));
        when(mockAuthRepository.getUserByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(createUser(ADMIN_EMAIL, true, null)));

        AuthzHandler.AuthorizationResult readResult = authzHandler.checkPermission(VALID_TOKEN, TEST_DIRECTORY, PermissionConstants.READ);
        AuthzHandler.AuthorizationResult createResult = authzHandler.checkPermission(VALID_TOKEN, TEST_DIRECTORY, PermissionConstants.CREATE);
        AuthzHandler.AuthorizationResult updateResult = authzHandler.checkPermission(VALID_TOKEN, TEST_DIRECTORY, PermissionConstants.UPDATE);
        AuthzHandler.AuthorizationResult deleteResult = authzHandler.checkPermission(VALID_TOKEN, TEST_DIRECTORY, PermissionConstants.DELETE);

        assertTrue(readResult.isAuthorized(), "Admin should have READ access");
        assertTrue(createResult.isAuthorized(), "Admin should have CREATE access");
        assertTrue(updateResult.isAuthorized(), "Admin should have UPDATE access");
        assertTrue(deleteResult.isAuthorized(), "Admin should have DELETE access");
    }

    @Test
    @DisplayName("2. User with READ permission can read but not write")
    public void testUserWithReadOnlyPermission() {
        Map<String, Integer> permissions = new HashMap<>();
        permissions.put(TEST_DIRECTORY, PermissionConstants.READ);

        when(mockSessionRepository.getSession(VALID_TOKEN))
                .thenReturn(Optional.of(createSession(VALID_TOKEN, TEST_USER_EMAIL)));
        when(mockAuthRepository.getUserByEmail(TEST_USER_EMAIL))
                .thenReturn(Optional.of(createUser(TEST_USER_EMAIL, false, permissions)));

        AuthzHandler.AuthorizationResult readResult = authzHandler.checkPermission(VALID_TOKEN, TEST_DIRECTORY, PermissionConstants.READ);
        AuthzHandler.AuthorizationResult createResult = authzHandler.checkPermission(VALID_TOKEN, TEST_DIRECTORY, PermissionConstants.CREATE);
        AuthzHandler.AuthorizationResult updateResult = authzHandler.checkPermission(VALID_TOKEN, TEST_DIRECTORY, PermissionConstants.UPDATE);
        AuthzHandler.AuthorizationResult deleteResult = authzHandler.checkPermission(VALID_TOKEN, TEST_DIRECTORY, PermissionConstants.DELETE);

        assertTrue(readResult.isAuthorized(), "User should have READ access");
        assertFalse(createResult.isAuthorized(), "User should NOT have CREATE access");
        assertFalse(updateResult.isAuthorized(), "User should NOT have UPDATE access");
        assertFalse(deleteResult.isAuthorized(), "User should NOT have DELETE access");
    }

    @Test
    @DisplayName("3. User with full CRUD permissions has all access")
    public void testUserWithFullCRUDPermissions() {
        Map<String, Integer> permissions = new HashMap<>();
        permissions.put(TEST_DIRECTORY, PermissionConstants.FULL_ACCESS);

        when(mockSessionRepository.getSession(VALID_TOKEN))
                .thenReturn(Optional.of(createSession(VALID_TOKEN, TEST_USER_EMAIL)));
        when(mockAuthRepository.getUserByEmail(TEST_USER_EMAIL))
                .thenReturn(Optional.of(createUser(TEST_USER_EMAIL, false, permissions)));

        AuthzHandler.AuthorizationResult readResult = authzHandler.checkPermission(VALID_TOKEN, TEST_DIRECTORY, PermissionConstants.READ);
        AuthzHandler.AuthorizationResult createResult = authzHandler.checkPermission(VALID_TOKEN, TEST_DIRECTORY, PermissionConstants.CREATE);
        AuthzHandler.AuthorizationResult updateResult = authzHandler.checkPermission(VALID_TOKEN, TEST_DIRECTORY, PermissionConstants.UPDATE);
        AuthzHandler.AuthorizationResult deleteResult = authzHandler.checkPermission(VALID_TOKEN, TEST_DIRECTORY, PermissionConstants.DELETE);

        assertTrue(readResult.isAuthorized(), "User should have READ access");
        assertTrue(createResult.isAuthorized(), "User should have CREATE access");
        assertTrue(updateResult.isAuthorized(), "User should have UPDATE access");
        assertTrue(deleteResult.isAuthorized(), "User should have DELETE access");
    }

    @Test
    @DisplayName("3b. User with MODIFY_ONLY permission can read and update but cannot create or delete")
    public void testUserWithModifyOnlyPermission() {
        Map<String, Integer> permissions = new HashMap<>();
        // MODIFY_ONLY = READ | UPDATE (6) - can modify existing entries without adding new entries
        permissions.put(TEST_DIRECTORY, PermissionConstants.MODIFY_ONLY);

        when(mockSessionRepository.getSession(VALID_TOKEN))
                .thenReturn(Optional.of(createSession(VALID_TOKEN, TEST_USER_EMAIL)));
        when(mockAuthRepository.getUserByEmail(TEST_USER_EMAIL))
                .thenReturn(Optional.of(createUser(TEST_USER_EMAIL, false, permissions)));

        AuthzHandler.AuthorizationResult readResult = authzHandler.checkPermission(VALID_TOKEN, TEST_DIRECTORY, PermissionConstants.READ);
        AuthzHandler.AuthorizationResult createResult = authzHandler.checkPermission(VALID_TOKEN, TEST_DIRECTORY, PermissionConstants.CREATE);
        AuthzHandler.AuthorizationResult updateResult = authzHandler.checkPermission(VALID_TOKEN, TEST_DIRECTORY, PermissionConstants.UPDATE);
        AuthzHandler.AuthorizationResult deleteResult = authzHandler.checkPermission(VALID_TOKEN, TEST_DIRECTORY, PermissionConstants.DELETE);

        assertTrue(readResult.isAuthorized(), "User with MODIFY_ONLY should have READ access");
        assertTrue(updateResult.isAuthorized(), "User with MODIFY_ONLY should have UPDATE access");
        assertFalse(createResult.isAuthorized(), "User with MODIFY_ONLY should NOT have CREATE access (cannot add new entries)");
        assertFalse(deleteResult.isAuthorized(), "User with MODIFY_ONLY should NOT have DELETE access");
    }

    @Test
    @DisplayName("3c. User with READ_WRITE permission can create, read, update but cannot delete")
    public void testUserWithReadWriteWithoutDelete() {
        Map<String, Integer> permissions = new HashMap<>();
        // READ_WRITE = CREATE | READ | UPDATE (7)
        permissions.put(TEST_DIRECTORY, PermissionConstants.READ_WRITE);

        when(mockSessionRepository.getSession(VALID_TOKEN))
                .thenReturn(Optional.of(createSession(VALID_TOKEN, TEST_USER_EMAIL)));
        when(mockAuthRepository.getUserByEmail(TEST_USER_EMAIL))
                .thenReturn(Optional.of(createUser(TEST_USER_EMAIL, false, permissions)));

        AuthzHandler.AuthorizationResult readResult = authzHandler.checkPermission(VALID_TOKEN, TEST_DIRECTORY, PermissionConstants.READ);
        AuthzHandler.AuthorizationResult createResult = authzHandler.checkPermission(VALID_TOKEN, TEST_DIRECTORY, PermissionConstants.CREATE);
        AuthzHandler.AuthorizationResult updateResult = authzHandler.checkPermission(VALID_TOKEN, TEST_DIRECTORY, PermissionConstants.UPDATE);
        AuthzHandler.AuthorizationResult deleteResult = authzHandler.checkPermission(VALID_TOKEN, TEST_DIRECTORY, PermissionConstants.DELETE);

        assertTrue(readResult.isAuthorized(), "User should have READ access");
        assertTrue(createResult.isAuthorized(), "User should have CREATE access");
        assertTrue(updateResult.isAuthorized(), "User should have UPDATE access");
        assertFalse(deleteResult.isAuthorized(), "User should NOT have DELETE access");
    }

    @Test
    @DisplayName("4. Invalid session token returns Unauthorized")
    public void testInvalidSessionTokenReturnsUnauthorized() {
        when(mockSessionRepository.getSession(INVALID_TOKEN))
                .thenReturn(Optional.empty());

        AuthzHandler.AuthorizationResult result = authzHandler.checkPermission(INVALID_TOKEN, TEST_DIRECTORY, PermissionConstants.READ);

        assertFalse(result.isAuthorized());
        assertTrue(result.isUnauthorized(), "Should be 401 Unauthorized");
        assertTrue(result.getErrorMessage().contains("Unauthorized"));
    }

    @Test
    @DisplayName("5. Empty session token returns Unauthorized")
    public void testEmptySessionTokenReturnsUnauthorized() {
        AuthzHandler.AuthorizationResult result = authzHandler.checkPermission("", TEST_DIRECTORY, PermissionConstants.READ);

        assertFalse(result.isAuthorized());
        assertTrue(result.isUnauthorized());
        assertTrue(result.getErrorMessage().contains("Unauthorized"));
    }

    @Test
    @DisplayName("6. Null session token returns Unauthorized")
    public void testNullSessionTokenReturnsUnauthorized() {
        AuthzHandler.AuthorizationResult result = authzHandler.checkPermission(null, TEST_DIRECTORY, PermissionConstants.READ);

        assertFalse(result.isAuthorized());
        assertTrue(result.isUnauthorized());
        assertTrue(result.getErrorMessage().contains("No session token provided"));
    }

    @Test
    @DisplayName("7. User without permission for directory returns Forbidden")
    public void testUserWithoutDirectoryPermissionReturnsForbidden() {
        Map<String, Integer> permissions = new HashMap<>();
        permissions.put("other-directory", PermissionConstants.FULL_ACCESS);
        // No permission for TEST_DIRECTORY

        when(mockSessionRepository.getSession(VALID_TOKEN))
                .thenReturn(Optional.of(createSession(VALID_TOKEN, TEST_USER_EMAIL)));
        when(mockAuthRepository.getUserByEmail(TEST_USER_EMAIL))
                .thenReturn(Optional.of(createUser(TEST_USER_EMAIL, false, permissions)));

        AuthzHandler.AuthorizationResult result = authzHandler.checkPermission(VALID_TOKEN, TEST_DIRECTORY, PermissionConstants.READ);

        assertFalse(result.isAuthorized());
        assertFalse(result.isUnauthorized(), "Should be 403 Forbidden, not 401");
        assertTrue(result.getErrorMessage().contains("Forbidden"));
    }

    @Test
    @DisplayName("8. User with CREATE+UPDATE but no DELETE cannot delete")
    public void testUserWithPartialPermissions() {
        Map<String, Integer> permissions = new HashMap<>();
        // CREATE (1) + UPDATE (4) = 5
        permissions.put(TEST_DIRECTORY, PermissionConstants.CREATE | PermissionConstants.UPDATE);

        when(mockSessionRepository.getSession(VALID_TOKEN))
                .thenReturn(Optional.of(createSession(VALID_TOKEN, TEST_USER_EMAIL)));
        when(mockAuthRepository.getUserByEmail(TEST_USER_EMAIL))
                .thenReturn(Optional.of(createUser(TEST_USER_EMAIL, false, permissions)));

        AuthzHandler.AuthorizationResult createResult = authzHandler.checkPermission(VALID_TOKEN, TEST_DIRECTORY, PermissionConstants.CREATE);
        AuthzHandler.AuthorizationResult updateResult = authzHandler.checkPermission(VALID_TOKEN, TEST_DIRECTORY, PermissionConstants.UPDATE);
        AuthzHandler.AuthorizationResult deleteResult = authzHandler.checkPermission(VALID_TOKEN, TEST_DIRECTORY, PermissionConstants.DELETE);
        AuthzHandler.AuthorizationResult readResult = authzHandler.checkPermission(VALID_TOKEN, TEST_DIRECTORY, PermissionConstants.READ);

        assertTrue(createResult.isAuthorized(), "User should have CREATE access");
        assertTrue(updateResult.isAuthorized(), "User should have UPDATE access");
        assertFalse(deleteResult.isAuthorized(), "User should NOT have DELETE access");
        assertFalse(readResult.isAuthorized(), "User should NOT have READ access");
    }

    @Test
    @DisplayName("9. User not found in auth repository returns Unauthorized")
    public void testUserNotFoundReturnsUnauthorized() {
        when(mockSessionRepository.getSession(VALID_TOKEN))
                .thenReturn(Optional.of(createSession(VALID_TOKEN, TEST_USER_EMAIL)));
        when(mockAuthRepository.getUserByEmail(TEST_USER_EMAIL))
                .thenReturn(Optional.empty());

        AuthzHandler.AuthorizationResult result = authzHandler.checkPermission(VALID_TOKEN, TEST_DIRECTORY, PermissionConstants.READ);

        assertFalse(result.isAuthorized());
        assertTrue(result.isUnauthorized());
        assertTrue(result.getErrorMessage().contains("User not found"));
    }

    @Test
    @DisplayName("10. QueryHandler returns error when authorization fails")
    public void testQueryHandlerReturnsErrorOnAuthorizationFailure() throws Exception {
        // User with no permissions
        when(mockSessionRepository.getSession(VALID_TOKEN))
                .thenReturn(Optional.of(createSession(VALID_TOKEN, TEST_USER_EMAIL)));
        when(mockAuthRepository.getUserByEmail(TEST_USER_EMAIL))
                .thenReturn(Optional.of(createUser(TEST_USER_EMAIL, false, new HashMap<>())));

        UserQuery query = createQuery(QueryType.GET, "testKey", "", TEST_DIRECTORY, VALID_TOKEN);
        QueryResponse response = executeQuery(query);

        assertFalse(response.getSuccess(), "Query should fail");
        assertTrue(response.getErrorMessage().contains("Forbidden") || 
                   response.getErrorMessage().contains("No permission"),
                   "Error should indicate permission denied");
    }

    @Test
    @DisplayName("11. Admin caller can set admin status")
    public void testAdminCallerCanSetAdmin() {
        String adminToken = "admin-token-123";
        String targetUser = "target@example.com";

        when(mockSessionRepository.getSession(adminToken))
                .thenReturn(Optional.of(createSession(adminToken, ADMIN_EMAIL)));
        when(mockAuthRepository.getUserByEmail(ADMIN_EMAIL))
                .thenReturn(Optional.of(createUser(ADMIN_EMAIL, true, null)));
        when(mockAuthRepository.getUserByEmail(targetUser))
                .thenReturn(Optional.of(createUser(targetUser, false, null)));

        UserPermissionsRequest request = UserPermissionsRequest.newBuilder()
                .setRequestType(RequestType.SET_IS_ADMIN)
                .setUserEmail(targetUser)
                .setToken(adminToken)
                .setUserPermissions(UserPermissions.newBuilder()
                        .setIsAdmin(true)
                        .build())
                .build();

        UserPermissionsResponse response = authzHandler.setIsAdmin(request);

        assertTrue(response.getSuccess());
        assertTrue(response.getUserPermissions().getIsAdmin());
        verify(mockAuthRepository).updateUser(argThat(UserAuth::getIsAdmin));
    }

    @Test
    @DisplayName("12. Non-admin caller cannot set admin status")
    public void testNonAdminCallerCannotSetAdmin() {
        String userToken = "user-token-123";
        String targetUser = "target@example.com";

        when(mockSessionRepository.getSession(userToken))
                .thenReturn(Optional.of(createSession(userToken, TEST_USER_EMAIL)));
        when(mockAuthRepository.getUserByEmail(TEST_USER_EMAIL))
                .thenReturn(Optional.of(createUser(TEST_USER_EMAIL, false, null)));

        UserPermissionsRequest request = UserPermissionsRequest.newBuilder()
                .setRequestType(RequestType.SET_IS_ADMIN)
                .setUserEmail(targetUser)
                .setToken(userToken)
                .setUserPermissions(UserPermissions.newBuilder()
                        .setIsAdmin(true)
                        .build())
                .build();

        UserPermissionsResponse response = authzHandler.setIsAdmin(request);

        assertFalse(response.getSuccess());
        assertTrue(response.getErrorMessage().contains("Admin privileges required"));
        verify(mockAuthRepository, never()).updateUser(any());
    }

    @Test
    @DisplayName("13. Non-admin caller cannot set directory permissions")
    public void testNonAdminCallerCannotSetDirectoryPermissions() {
        String userToken = "user-token-123";
        String targetUser = "target@example.com";

        when(mockSessionRepository.getSession(userToken))
                .thenReturn(Optional.of(createSession(userToken, TEST_USER_EMAIL)));
        when(mockAuthRepository.getUserByEmail(TEST_USER_EMAIL))
                .thenReturn(Optional.of(createUser(TEST_USER_EMAIL, false, null)));

        UserPermissionsRequest request = UserPermissionsRequest.newBuilder()
                .setRequestType(RequestType.SET_DIRECTORY_PERMISSIONS)
                .setUserEmail(targetUser)
                .setToken(userToken)
                .setUserPermissions(UserPermissions.newBuilder()
                        .putDirectoryPermissions(TEST_DIRECTORY, PermissionConstants.FULL_ACCESS)
                        .build())
                .build();

        UserPermissionsResponse response = authzHandler.setDirectoriesPermissions(request);

        assertFalse(response.getSuccess());
        assertTrue(response.getErrorMessage().contains("Admin privileges required"));
        verify(mockAuthRepository, never()).updateUser(any());
    }

    @Test
    @DisplayName("14. User can view their own permissions")
    public void testUserCanViewOwnPermissions() {
        String userToken = "user-token-123";

        when(mockSessionRepository.getSession(userToken))
                .thenReturn(Optional.of(createSession(userToken, TEST_USER_EMAIL)));
        when(mockAuthRepository.getUserByEmail(TEST_USER_EMAIL))
                .thenReturn(Optional.of(createUser(TEST_USER_EMAIL, false, Map.of(TEST_DIRECTORY, 3))));

        UserPermissionsRequest request = UserPermissionsRequest.newBuilder()
                .setRequestType(RequestType.GET)
                .setUserEmail(TEST_USER_EMAIL)
                .setToken(userToken)
                .build();

        UserPermissionsResponse response = authzHandler.getUserPermissionsByEmail(request);

        assertTrue(response.getSuccess());
        assertEquals(3, response.getUserPermissions().getDirectoryPermissionsMap().get(TEST_DIRECTORY));
    }

    @Test
    @DisplayName("15. Non-admin cannot view other users permissions")
    public void testNonAdminCannotViewOtherUserPermissions() {
        String userToken = "user-token-123";
        String otherUser = "other@example.com";

        when(mockSessionRepository.getSession(userToken))
                .thenReturn(Optional.of(createSession(userToken, TEST_USER_EMAIL)));
        when(mockAuthRepository.getUserByEmail(TEST_USER_EMAIL))
                .thenReturn(Optional.of(createUser(TEST_USER_EMAIL, false, null)));

        UserPermissionsRequest request = UserPermissionsRequest.newBuilder()
                .setRequestType(RequestType.GET)
                .setUserEmail(otherUser)
                .setToken(userToken)
                .build();

        UserPermissionsResponse response = authzHandler.getUserPermissionsByEmail(request);

        assertFalse(response.getSuccess());
        assertTrue(response.getErrorMessage().contains("Forbidden") || response.getErrorMessage().contains("Insufficient permissions"));
    }
}
