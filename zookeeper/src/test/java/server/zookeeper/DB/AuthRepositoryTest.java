package server.zookeeper.DB;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import server.zookeeper.proto.auth.UserAuth;
import server.zookeeper.util.ReservedDirectories;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("AuthRepository Tests")
class AuthRepositoryTest {

    @Mock
    private DataBase mockDatabase;

    private AuthRepository authRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        authRepository = new AuthRepository(mockDatabase);
    }

    @Nested
    @DisplayName("Save User Tests")
    class SaveUserTests {

        @Test
        @DisplayName("Should save user successfully")
        void saveUser_withValidUser_succeeds() {
            UserAuth user = createTestUser("test@example.com", "hashed_password", false);
            authRepository.saveUser(user);
            verify(mockDatabase, times(1)).put(
                    any(byte[].class),
                    any(byte[].class),
                    eq(ReservedDirectories.AUTH_DIRECTORY)
            );
        }

        @Test
        @DisplayName("Should throw exception when user is null")
        void saveUser_withNullUser_throwsException() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> authRepository.saveUser(null)
            );

            assertTrue(exception.getMessage().contains("cannot be null"));
        }

        @Test
        @DisplayName("Should throw exception when email is null or empty")
        void saveUser_withNullOrEmptyEmail_throwsException() {
            UserAuth user = UserAuth.newBuilder()
                    .setPasswordHash("hashed_password")
                    .build();

            UserAuth finalUser = user;
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> authRepository.saveUser(finalUser)
            );

            assertTrue(exception.getMessage().contains("Email"));

            user = createTestUser("", "hashed_password", false);

            UserAuth finalUser1 = user;
            exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> authRepository.saveUser(finalUser1)
            );

            assertTrue(exception.getMessage().contains("Email"));
        }


        @Test
        @DisplayName("Should throw exception when password hash is empty")
        void saveUser_withEmptyPasswordHash_throwsException() {
            UserAuth user = createTestUser("test@example.com", "", false);

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> authRepository.saveUser(user)
            );

            assertTrue(exception.getMessage().contains("Password hash"));
        }

        @Test
        @DisplayName("Should throw exception when email has invalid format")
        void saveUser_withInvalidEmail_throwsException() {
            UserAuth user = createTestUser("invalid-email", "hashed_password", false);

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> authRepository.saveUser(user)
            );
        }

        @Test
        @DisplayName("Should save user with permissions")
        void saveUser_withPermissions_succeeds() {
            Map<String, Integer> permissions = new HashMap<>();
            permissions.put("data", 15); // Full CRUD
            permissions.put("logs", 2);  // Read-only

            UserAuth user = UserAuth.newBuilder()
                    .setEmail("admin@example.com")
                    .setPasswordHash("hashed_password")
                    .setIsAdmin(true)
                    .putAllPermissions(permissions)
                    .setCanCreateDirectories(true)
                    .build();

            authRepository.saveUser(user);

            verify(mockDatabase, times(1)).put(any(), any(), anyString());
        }
    }

    @Nested
    @DisplayName("Get User Tests")
    class GetUserTests {

        @Test
        @DisplayName("Should retrieve existing user")
        void getUserByEmail_whenUserExists_returnsUser() throws Exception {
            String email = "test@example.com";
            UserAuth expectedUser = createTestUser(email, "hashed_password", false);
            byte[] serializedUser = expectedUser.toByteArray();

            when(mockDatabase.get(any(byte[].class), eq(ReservedDirectories.AUTH_DIRECTORY)))
                    .thenReturn(serializedUser);

            Optional<UserAuth> result = authRepository.getUserByEmail(email);

            assertTrue(result.isPresent());
            assertEquals(email, result.get().getEmail());
            assertEquals(expectedUser.getPasswordHash(), result.get().getPasswordHash());
            assertEquals(expectedUser.getIsAdmin(), result.get().getIsAdmin());
        }

        @Test
        @DisplayName("Should return empty when user doesn't exist")
        void getUserByEmail_whenUserDoesNotExist_returnsEmpty() {
            when(mockDatabase.get(any(byte[].class), anyString()))
                    .thenReturn(null);

            Optional<UserAuth> result = authRepository.getUserByEmail("nonexistent@example.com");

            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("Should throw exception when email is null or empty")
        void getUserByEmail_withNullOrEmptyEmail_throwsException() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> authRepository.getUserByEmail(null)
            );

            assertTrue(exception.getMessage().contains("Email"));

            exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> authRepository.getUserByEmail("")
            );

            assertTrue(exception.getMessage().contains("Email"));
        }

    }

    @Nested
    @DisplayName("Delete User Tests")
    class DeleteUserTests {

        @Test
        @DisplayName("Should delete user successfully")
        void deleteUser_withValidEmail_succeeds() {
            authRepository.deleteUser("test@example.com");

            verify(mockDatabase, times(1)).delete(
                    any(byte[].class),
                    eq(ReservedDirectories.AUTH_DIRECTORY)
            );
        }

        @Test
        @DisplayName("Should throw exception when email is null")
        void deleteUser_withNullEmail_throwsException() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> authRepository.deleteUser(null)
            );

            assertTrue(exception.getMessage().contains("Email"));
        }

    }

    @Nested
    @DisplayName("Update User Tests")
    class UpdateUserTests {

        @Test
        @DisplayName("Should update existing user successfully")
        void updateUser_whenUserExists_succeeds() {
            UserAuth user = createTestUser("test@example.com", "new_hashed_password", true);
            when(mockDatabase.get(any(byte[].class), anyString()))
                    .thenReturn(new byte[]{1, 2, 3}); // User exists

            authRepository.updateUser(user);

            verify(mockDatabase, times(1)).put(any(), any(), anyString());
        }

        @Test
        @DisplayName("Should throw exception when user is null")
        void updateUser_withNullUser_throwsException() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> authRepository.updateUser(null)
            );

            assertTrue(exception.getMessage().contains("cannot be null"));
        }
    }

    private UserAuth createTestUser(String email, String passwordHash, boolean isAdmin) {
        return UserAuth.newBuilder()
                .setEmail(email)
                .setPasswordHash(passwordHash)
                .setIsAdmin(isAdmin)
                .setCanCreateDirectories(false)
                .build();
    }
}