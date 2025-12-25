package webserver.zookeeper.zookeeper_webserver.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import webserver.zookeeper.zookeeper_webserver.dto.UpdatePermissionRequest;
import webserver.zookeeper.zookeeper_webserver.dto.UserDTO;
import webserver.zookeeper.zookeeper_webserver.services.UserPermissionService;
import webserver.zookeeper.zookeeper_webserver.services.ZookeeperService;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserPermissionControllerTest {

    @Mock
    private ZookeeperService zookeeperService;

    @Mock
    private UserPermissionService service;

    @InjectMocks
    private UserPermissionController controller;

    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_SESSION_TOKEN = "test-session-token-123";
    private static final String TEST_DIRECTORY = "/test/directory";

    @BeforeEach
    void setUp() {
        // InjectMocks will handle the injection, but we can ensure fields are set
        ReflectionTestUtils.setField(controller, "zookeeperService", zookeeperService);
        ReflectionTestUtils.setField(controller, "service", service);
    }

    @Test
    void getPermissions_Success() {
        // Arrange
        UserDTO expectedDTO = new UserDTO();
        expectedDTO.setEmail(TEST_EMAIL);
        expectedDTO.setAdmin(true);
        Map<String, Integer> permissionsMap = new HashMap<>();
        permissionsMap.put(TEST_DIRECTORY, 15);
        expectedDTO.setPermissions(permissionsMap);

        doNothing().when(zookeeperService).setToken(TEST_SESSION_TOKEN);
        when(service.getUserPermissions(TEST_EMAIL)).thenReturn(expectedDTO);

        // Act
        UserDTO result = controller.getPermissions(TEST_EMAIL, TEST_SESSION_TOKEN);

        // Assert
        assertNotNull(result);
        assertEquals(TEST_EMAIL, result.getEmail());
        assertTrue(result.isAdmin());
        assertEquals(permissionsMap, result.getPermissions());
        verify(zookeeperService).setToken(TEST_SESSION_TOKEN);
        verify(service).getUserPermissions(TEST_EMAIL);
    }

    @Test
    void getPermissions_SetsSessionToken() {
        // Arrange
        UserDTO userDTO = new UserDTO();
        userDTO.setEmail(TEST_EMAIL);
        doNothing().when(zookeeperService).setToken(TEST_SESSION_TOKEN);
        when(service.getUserPermissions(TEST_EMAIL)).thenReturn(userDTO);

        // Act
        controller.getPermissions(TEST_EMAIL, TEST_SESSION_TOKEN);

        // Assert
        verify(zookeeperService).setToken(TEST_SESSION_TOKEN);
        verify(service).getUserPermissions(TEST_EMAIL);
    }

    @Test
    void getPermissions_WithDifferentEmail() {
        // Arrange
        String differentEmail = "different@example.com";
        UserDTO userDTO = new UserDTO();
        userDTO.setEmail(differentEmail);
        doNothing().when(zookeeperService).setToken(TEST_SESSION_TOKEN);
        when(service.getUserPermissions(differentEmail)).thenReturn(userDTO);

        // Act
        UserDTO result = controller.getPermissions(differentEmail, TEST_SESSION_TOKEN);

        // Assert
        assertEquals(differentEmail, result.getEmail());
        verify(service).getUserPermissions(differentEmail);
        verify(zookeeperService).setToken(TEST_SESSION_TOKEN);
    }

    @Test
    void updatePermission_Success() {
        // Arrange
        UpdatePermissionRequest request = new UpdatePermissionRequest();
        request.setDirectory(TEST_DIRECTORY);
        request.setPermissions(Arrays.asList("create", "read", "update", "delete"));

        doNothing().when(zookeeperService).setToken(TEST_SESSION_TOKEN);
        doNothing().when(service).updatePermission(eq(TEST_EMAIL), any(UpdatePermissionRequest.class));

        // Act
        ResponseEntity<Void> response = controller.updatePermission(TEST_EMAIL, request, TEST_SESSION_TOKEN);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(zookeeperService).setToken(TEST_SESSION_TOKEN);
        verify(service).updatePermission(TEST_EMAIL, request);
    }

    @Test
    void updatePermission_SetsSessionToken() {
        // Arrange
        UpdatePermissionRequest request = new UpdatePermissionRequest();
        request.setDirectory(TEST_DIRECTORY);
        request.setPermissions(Arrays.asList("read"));

        doNothing().when(zookeeperService).setToken(TEST_SESSION_TOKEN);

        // Act
        controller.updatePermission(TEST_EMAIL, request, TEST_SESSION_TOKEN);

        // Assert
        verify(zookeeperService).setToken(TEST_SESSION_TOKEN);
        verify(service).updatePermission(eq(TEST_EMAIL), eq(request));
    }

    @Test
    void updatePermission_WithEmptyPermissions() {
        // Arrange
        UpdatePermissionRequest request = new UpdatePermissionRequest();
        request.setDirectory(TEST_DIRECTORY);
        request.setPermissions(Arrays.asList());

        doNothing().when(zookeeperService).setToken(TEST_SESSION_TOKEN);

        // Act
        ResponseEntity<Void> response = controller.updatePermission(TEST_EMAIL, request, TEST_SESSION_TOKEN);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(service).updatePermission(TEST_EMAIL, request);
        verify(zookeeperService).setToken(TEST_SESSION_TOKEN);
    }

    @Test
    void setCanCreateDirectory_Success() {
        // Arrange
        doNothing().when(zookeeperService).setToken(TEST_SESSION_TOKEN);
        doNothing().when(service).setCanCreateDirectory(TEST_EMAIL);

        // Act
        ResponseEntity<Void> response = controller.setCanCreateDirectory(TEST_EMAIL, TEST_SESSION_TOKEN);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(zookeeperService).setToken(TEST_SESSION_TOKEN);
        verify(service).setCanCreateDirectory(TEST_EMAIL);
    }

    @Test
    void setCanCreateDirectory_SetsSessionToken() {
        // Arrange
        doNothing().when(zookeeperService).setToken(TEST_SESSION_TOKEN);

        // Act
        controller.setCanCreateDirectory(TEST_EMAIL, TEST_SESSION_TOKEN);

        // Assert
        verify(zookeeperService).setToken(TEST_SESSION_TOKEN);
        verify(service).setCanCreateDirectory(TEST_EMAIL);
    }

    @Test
    void setIsAdmin_Success() {
        // Arrange
        doNothing().when(zookeeperService).setToken(TEST_SESSION_TOKEN);
        doNothing().when(service).setIsAdmin(TEST_EMAIL);

        // Act
        ResponseEntity<Void> response = controller.setIsAdmin(TEST_EMAIL, TEST_SESSION_TOKEN);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(zookeeperService).setToken(TEST_SESSION_TOKEN);
        verify(service).setIsAdmin(TEST_EMAIL);
    }

    @Test
    void setIsAdmin_SetsSessionToken() {
        // Arrange
        doNothing().when(zookeeperService).setToken(TEST_SESSION_TOKEN);

        // Act
        controller.setIsAdmin(TEST_EMAIL, TEST_SESSION_TOKEN);

        // Assert
        verify(zookeeperService).setToken(TEST_SESSION_TOKEN);
        verify(service).setIsAdmin(TEST_EMAIL);
    }

    @Test
    void getPermissions_HandlesServiceException() {
        // Arrange
        doNothing().when(zookeeperService).setToken(TEST_SESSION_TOKEN);
        when(service.getUserPermissions(TEST_EMAIL))
                .thenThrow(new RuntimeException("Service error"));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            controller.getPermissions(TEST_EMAIL, TEST_SESSION_TOKEN);
        });

        verify(zookeeperService).setToken(TEST_SESSION_TOKEN);
        verify(service).getUserPermissions(TEST_EMAIL);
    }

    @Test
    void updatePermission_HandlesServiceException() {
        // Arrange
        UpdatePermissionRequest request = new UpdatePermissionRequest();
        request.setDirectory(TEST_DIRECTORY);
        request.setPermissions(Arrays.asList("read"));

        doNothing().when(zookeeperService).setToken(TEST_SESSION_TOKEN);
        doThrow(new RuntimeException("Service error"))
                .when(service).updatePermission(eq(TEST_EMAIL), any(UpdatePermissionRequest.class));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            controller.updatePermission(TEST_EMAIL, request, TEST_SESSION_TOKEN);
        });

        verify(zookeeperService).setToken(TEST_SESSION_TOKEN);
        verify(service).updatePermission(TEST_EMAIL, request);
    }
}