package webserver.zookeeper.zookeeper_webserver.services;

import client.zookeeper.ZookeeperClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import server.zookeeper.proto.permissions.UserPermissions;
import webserver.zookeeper.zookeeper_webserver.dto.UpdatePermissionRequest;
import webserver.zookeeper.zookeeper_webserver.dto.UserDTO;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserPermissionServiceTest {

    @Mock
    private ZookeeperClient zkClient;

    @InjectMocks
    private UserPermissionService userPermissionService;

    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_DIRECTORY = "/test/directory";

    @BeforeEach
    void setUp() {
        userPermissionService = new UserPermissionService(zkClient);
    }

    @Test
    void getUserPermissions_Success() {
        // Arrange
        Map<String, Integer> permissionsMap = new HashMap<>();
        permissionsMap.put(TEST_DIRECTORY, 15);

        UserPermissions userPermissions = UserPermissions.newBuilder()
                .setIsAdmin(true)
                .putAllDirectoryPermissions(permissionsMap)
                .build();

        ZookeeperClient.PermissionsResult result =
                ZookeeperClient.PermissionsResult.success("Success", userPermissions);

        when(zkClient.getUserPermissionsByEmail(TEST_EMAIL)).thenReturn(result);

        // Act
        UserDTO userDTO = userPermissionService.getUserPermissions(TEST_EMAIL);

        // Assert
        assertNotNull(userDTO);
        assertEquals(TEST_EMAIL, userDTO.getEmail());
        assertTrue(userDTO.isAdmin());
        assertEquals(permissionsMap, userDTO.getPermissions());
        verify(zkClient).getUserPermissionsByEmail(TEST_EMAIL);
    }

    @Test
    void getUserPermissions_Success_WithMultipleDirectories() {
        // Arrange
        Map<String, Integer> permissionsMap = new HashMap<>();
        permissionsMap.put("/dir1", 15);
        permissionsMap.put("/dir2", 7);
        permissionsMap.put("/dir3", 3);

        UserPermissions userPermissions = UserPermissions.newBuilder()
                .setIsAdmin(false)
                .putAllDirectoryPermissions(permissionsMap)
                .build();

        ZookeeperClient.PermissionsResult result =
                ZookeeperClient.PermissionsResult.success("Success", userPermissions);

        when(zkClient.getUserPermissionsByEmail(TEST_EMAIL)).thenReturn(result);

        // Act
        UserDTO userDTO = userPermissionService.getUserPermissions(TEST_EMAIL);

        // Assert
        assertNotNull(userDTO);
        assertEquals(TEST_EMAIL, userDTO.getEmail());
        assertFalse(userDTO.isAdmin());
        assertEquals(3, userDTO.getPermissions().size());
        assertEquals(permissionsMap, userDTO.getPermissions());
        verify(zkClient).getUserPermissionsByEmail(TEST_EMAIL);
    }

    @Test
    void getUserPermissions_Success_WithNoPermissions() {
        // Arrange
        UserPermissions userPermissions = UserPermissions.newBuilder()
                .setIsAdmin(false)
                .build();

        ZookeeperClient.PermissionsResult result =
                ZookeeperClient.PermissionsResult.success("Success", userPermissions);

        when(zkClient.getUserPermissionsByEmail(TEST_EMAIL)).thenReturn(result);

        // Act
        UserDTO userDTO = userPermissionService.getUserPermissions(TEST_EMAIL);

        // Assert
        assertNotNull(userDTO);
        assertEquals(TEST_EMAIL, userDTO.getEmail());
        assertFalse(userDTO.isAdmin());
        assertTrue(userDTO.getPermissions().isEmpty());
        verify(zkClient).getUserPermissionsByEmail(TEST_EMAIL);
    }

    @Test
    void getUserPermissions_Failure_ThrowsException() {
        // Arrange
        String errorMessage = "User not found";
        ZookeeperClient.PermissionsResult result =
                ZookeeperClient.PermissionsResult.failure(errorMessage);

        when(zkClient.getUserPermissionsByEmail(TEST_EMAIL)).thenReturn(result);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            userPermissionService.getUserPermissions(TEST_EMAIL);
        });

        assertEquals(errorMessage, exception.getMessage());
        verify(zkClient).getUserPermissionsByEmail(TEST_EMAIL);
    }

    @Test
    void setIsAdmin_Success() {
        // Arrange
        ZookeeperClient.PermissionsResult result =
                ZookeeperClient.PermissionsResult.success("Admin set successfully", null);
        when(zkClient.setIsAdmin(TEST_EMAIL, true)).thenReturn(result);

        // Act
        userPermissionService.setIsAdmin(TEST_EMAIL);

        // Assert
        verify(zkClient).setIsAdmin(TEST_EMAIL, true);
    }

    @Test
    void setCanCreateDirectory_Success() {
        // Arrange
        ZookeeperClient.PermissionsResult result =
                ZookeeperClient.PermissionsResult.success("Can create directories set", null);
        when(zkClient.setCanCreateDirectories(TEST_EMAIL, true)).thenReturn(result);

        // Act
        userPermissionService.setCanCreateDirectory(TEST_EMAIL);

        // Assert
        verify(zkClient).setCanCreateDirectories(TEST_EMAIL, true);
    }

    @Test
    void updatePermission_WithAllPermissions() {
        // Arrange
        UpdatePermissionRequest request = new UpdatePermissionRequest();
        request.setDirectory(TEST_DIRECTORY);
        request.setPermissions(Arrays.asList("create", "read", "update", "delete"));

        ZookeeperClient.PermissionsResult result =
                ZookeeperClient.PermissionsResult.success("Permissions updated", null);
        when(zkClient.setDirectoryPermissions(eq(TEST_EMAIL), anyMap())).thenReturn(result);

        // Act
        userPermissionService.updatePermission(TEST_EMAIL, request);

        // Assert
        verify(zkClient).setDirectoryPermissions(eq(TEST_EMAIL), argThat(map ->
                map.containsKey(TEST_DIRECTORY) && map.get(TEST_DIRECTORY) == 15
        ));
    }

    @Test
    void updatePermission_WithCreateOnly() {
        // Arrange
        UpdatePermissionRequest request = new UpdatePermissionRequest();
        request.setDirectory(TEST_DIRECTORY);
        request.setPermissions(List.of("create"));

        ZookeeperClient.PermissionsResult result =
                ZookeeperClient.PermissionsResult.success("Permissions updated", null);
        when(zkClient.setDirectoryPermissions(eq(TEST_EMAIL), anyMap())).thenReturn(result);

        // Act
        userPermissionService.updatePermission(TEST_EMAIL, request);

        // Assert
        verify(zkClient).setDirectoryPermissions(eq(TEST_EMAIL), argThat(map ->
                map.containsKey(TEST_DIRECTORY) && map.get(TEST_DIRECTORY) == 1
        ));
    }

    @Test
    void updatePermission_WithReadOnly() {
        // Arrange
        UpdatePermissionRequest request = new UpdatePermissionRequest();
        request.setDirectory(TEST_DIRECTORY);
        request.setPermissions(List.of("read"));

        ZookeeperClient.PermissionsResult result =
                ZookeeperClient.PermissionsResult.success("Permissions updated", null);
        when(zkClient.setDirectoryPermissions(eq(TEST_EMAIL), anyMap())).thenReturn(result);

        // Act
        userPermissionService.updatePermission(TEST_EMAIL, request);

        // Assert
        verify(zkClient).setDirectoryPermissions(eq(TEST_EMAIL), argThat(map ->
                map.containsKey(TEST_DIRECTORY) && map.get(TEST_DIRECTORY) == 2
        ));
    }

    @Test
    void updatePermission_WithUpdateOnly() {
        // Arrange
        UpdatePermissionRequest request = new UpdatePermissionRequest();
        request.setDirectory(TEST_DIRECTORY);
        request.setPermissions(List.of("update"));

        ZookeeperClient.PermissionsResult result =
                ZookeeperClient.PermissionsResult.success("Permissions updated", null);
        when(zkClient.setDirectoryPermissions(eq(TEST_EMAIL), anyMap())).thenReturn(result);

        // Act
        userPermissionService.updatePermission(TEST_EMAIL, request);

        // Assert
        verify(zkClient).setDirectoryPermissions(eq(TEST_EMAIL), argThat(map ->
                map.containsKey(TEST_DIRECTORY) && map.get(TEST_DIRECTORY) == 4
        ));
    }

    @Test
    void updatePermission_WithDeleteOnly() {
        // Arrange
        UpdatePermissionRequest request = new UpdatePermissionRequest();
        request.setDirectory(TEST_DIRECTORY);
        request.setPermissions(List.of("delete"));

        ZookeeperClient.PermissionsResult result =
                ZookeeperClient.PermissionsResult.success("Permissions updated", null);
        when(zkClient.setDirectoryPermissions(eq(TEST_EMAIL), anyMap())).thenReturn(result);

        // Act
        userPermissionService.updatePermission(TEST_EMAIL, request);

        // Assert
        verify(zkClient).setDirectoryPermissions(eq(TEST_EMAIL), argThat(map ->
                map.containsKey(TEST_DIRECTORY) && map.get(TEST_DIRECTORY) == 8
        ));
    }

    @Test
    void updatePermission_WithMultiplePermissions() {
        // Arrange
        UpdatePermissionRequest request = new UpdatePermissionRequest();
        request.setDirectory(TEST_DIRECTORY);
        request.setPermissions(Arrays.asList("create", "read"));

        ZookeeperClient.PermissionsResult result =
                ZookeeperClient.PermissionsResult.success("Permissions updated", null);
        when(zkClient.setDirectoryPermissions(eq(TEST_EMAIL), anyMap())).thenReturn(result);

        // Act
        userPermissionService.updatePermission(TEST_EMAIL, request);

        // Assert
        verify(zkClient).setDirectoryPermissions(eq(TEST_EMAIL), argThat(map ->
                map.containsKey(TEST_DIRECTORY) && map.get(TEST_DIRECTORY) == 3
        ));
    }

    @Test
    void updatePermission_WithReadUpdateDelete() {
        // Arrange
        UpdatePermissionRequest request = new UpdatePermissionRequest();
        request.setDirectory(TEST_DIRECTORY);
        request.setPermissions(Arrays.asList("read", "update", "delete"));

        ZookeeperClient.PermissionsResult result =
                ZookeeperClient.PermissionsResult.success("Permissions updated", null);
        when(zkClient.setDirectoryPermissions(eq(TEST_EMAIL), anyMap())).thenReturn(result);

        // Act
        userPermissionService.updatePermission(TEST_EMAIL, request);

        // Assert (read=2, update=4, delete=8 -> 2+4+8=14)
        verify(zkClient).setDirectoryPermissions(eq(TEST_EMAIL), argThat(map ->
                map.containsKey(TEST_DIRECTORY) && map.get(TEST_DIRECTORY) == 14
        ));
    }

    @Test
    void updatePermission_WithNoPermissions() {
        // Arrange
        UpdatePermissionRequest request = new UpdatePermissionRequest();
        request.setDirectory(TEST_DIRECTORY);
        request.setPermissions(List.of());

        ZookeeperClient.PermissionsResult result =
                ZookeeperClient.PermissionsResult.success("Permissions updated", null);
        when(zkClient.setDirectoryPermissions(eq(TEST_EMAIL), anyMap())).thenReturn(result);

        // Act
        userPermissionService.updatePermission(TEST_EMAIL, request);

        // Assert
        verify(zkClient).setDirectoryPermissions(eq(TEST_EMAIL), argThat(map ->
                map.containsKey(TEST_DIRECTORY) && map.get(TEST_DIRECTORY) == 0
        ));
    }

    @Test
    void getPermissionNum_CalculatesCorrectly() {
        // This tests the private method through the public updatePermission method
        // Arrange
        UpdatePermissionRequest request = new UpdatePermissionRequest();
        request.setDirectory(TEST_DIRECTORY);
        request.setPermissions(Arrays.asList("create", "update"));

        ZookeeperClient.PermissionsResult result =
                ZookeeperClient.PermissionsResult.success("Permissions updated", null);
        when(zkClient.setDirectoryPermissions(eq(TEST_EMAIL), anyMap())).thenReturn(result);

        // Act
        userPermissionService.updatePermission(TEST_EMAIL, request);

        // Assert (create=1, update=4 -> 1+4=5)
        verify(zkClient).setDirectoryPermissions(eq(TEST_EMAIL), argThat(map ->
                map.containsKey(TEST_DIRECTORY) && map.get(TEST_DIRECTORY) == 5
        ));
    }
}