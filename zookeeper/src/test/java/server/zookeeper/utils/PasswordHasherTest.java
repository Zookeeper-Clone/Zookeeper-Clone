package server.zookeeper.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import server.zookeeper.util.PasswordHasher;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PasswordHasher Tests")
class PasswordHasherTest {

    private PasswordHasher passwordHasher;

    @BeforeEach
    void setUp() {
        // Use lower cost factor (4) for faster test execution
        passwordHasher = PasswordHasher.createForTesting(4);
    }

    @Nested
    @DisplayName("Singleton Pattern Tests")
    class SingletonTests {

        @Test
        @DisplayName("getInstance should return same instance")
        void getInstance_shouldReturnSameInstance() {
            PasswordHasher instance1 = PasswordHasher.getInstance();
            PasswordHasher instance2 = PasswordHasher.getInstance();

            assertNotNull(instance1);
            assertSame(instance1, instance2, "Should return the same singleton instance");
        }

        @Test
        @DisplayName("getInstance should be thread-safe")
        void getInstance_shouldBeThreadSafe() throws InterruptedException {
            final int threadCount = 10;
            final PasswordHasher[] instances = new PasswordHasher[threadCount];
            Thread[] threads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                threads[i] = new Thread(() -> instances[index] = PasswordHasher.getInstance());
                threads[i].start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            for (int i = 1; i < threadCount; i++) {
                assertSame(instances[0], instances[i],
                        "All threads should get the same instance");
            }
        }
    }

    @Nested
    @DisplayName("Password Hashing Tests")
    class HashingTests {

        @Test
        @DisplayName("hashPassword should produce valid BCrypt hash")
        void hashPassword_shouldProduceValidBCryptHash() {
            String password = "mySecurePassword123";
            String hash = passwordHasher.hashPassword(password);

            assertNotNull(hash);
            assertEquals(60, hash.length(), "BCrypt hash should be 60 characters");
            assertTrue(hash.startsWith("$2a$"), "BCrypt hash should start with $2a$");
        }

        @Test
        @DisplayName("hashPassword should produce different hashes for same password")
        void hashPassword_shouldProduceDifferentHashesForSamePassword() {
            String password = "testPassword";

            String hash1 = passwordHasher.hashPassword(password);
            String hash2 = passwordHasher.hashPassword(password);

            assertNotEquals(hash1, hash2,
                    "Same password should produce different hashes due to unique salts");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "short",
                "averageLength123",
                "VeryLongPasswordWith$pecialChar$AndNumbers1234567890",
                "unicode_测试_🔒"
        })
        @DisplayName("hashPassword should handle various password lengths")
        void hashPassword_shouldHandleVariousLengths(String password) {
            String hash = passwordHasher.hashPassword(password);

            assertNotNull(hash);
            assertEquals(60, hash.length());
            assertTrue(passwordHasher.verifyPassword(password, hash));
        }

        @Test
        @DisplayName("hashPassword should throw exception for null password")
        void hashPassword_withNullPassword_shouldThrowException() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> passwordHasher.hashPassword(null)
            );

            assertTrue(exception.getMessage().contains("cannot be null"));
        }

        @Test
        @DisplayName("hashPassword should throw exception for empty password")
        void hashPassword_withEmptyPassword_shouldThrowException() {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> passwordHasher.hashPassword("")
            );

            assertTrue(exception.getMessage().contains("cannot be null or empty"));
        }
    }

    @Nested
    @DisplayName("Password Verification Tests")
    class VerificationTests {

        @Test
        @DisplayName("verifyPassword should return true for correct password")
        void verifyPassword_withCorrectPassword_shouldReturnTrue() {
            String password = "correctPassword123";
            String hash = passwordHasher.hashPassword(password);

            boolean result = passwordHasher.verifyPassword(password, hash);

            assertTrue(result, "Correct password should verify successfully");
        }

        @Test
        @DisplayName("verifyPassword should return false for incorrect password")
        void verifyPassword_withIncorrectPassword_shouldReturnFalse() {
            String correctPassword = "correctPassword";
            String incorrectPassword = "wrongPassword";
            String hash = passwordHasher.hashPassword(correctPassword);

            boolean result = passwordHasher.verifyPassword(incorrectPassword, hash);

            assertFalse(result, "Incorrect password should fail verification");
        }

        @Test
        @DisplayName("verifyPassword should be case-sensitive")
        void verifyPassword_shouldBeCaseSensitive() {
            String password = "CaseSensitive";
            String hash = passwordHasher.hashPassword(password);

            assertFalse(passwordHasher.verifyPassword("casesensitive", hash));
            assertFalse(passwordHasher.verifyPassword("CASESENSITIVE", hash));
            assertTrue(passwordHasher.verifyPassword("CaseSensitive", hash));
        }

        @Test
        @DisplayName("verifyPassword should throw exception for null plaintext password")
        void verifyPassword_withNullPlainPassword_shouldThrowException() {
            String hash = passwordHasher.hashPassword("test");

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> passwordHasher.verifyPassword(null, hash)
            );

            assertTrue(exception.getMessage().contains("cannot be null"));
        }

        @Test
        @DisplayName("verifyPassword should return false for null hash")
        void verifyPassword_withNullHash_shouldReturnFalse() {
            boolean result = passwordHasher.verifyPassword("password", null);
            assertFalse(result);
        }

        @Test
        @DisplayName("verifyPassword should return false for empty hash")
        void verifyPassword_withEmptyHash_shouldReturnFalse() {
            boolean result = passwordHasher.verifyPassword("password", "");
            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("Security Tests")
    class SecurityTests {

        @Test
        @DisplayName("Password hashes should be unique even with concurrent access")
        void concurrentHashing_shouldProduceUniqueHashes() throws InterruptedException {
            final int threadCount = 10;
            final String password = "sharedPassword";
            final String[] hashes = new String[threadCount];
            Thread[] threads = new Thread[threadCount];

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                threads[i] = new Thread(() ->
                        hashes[index] = passwordHasher.hashPassword(password)
                );
                threads[i].start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            // All hashes should be different despite same password
            for (int i = 0; i < threadCount; i++) {
                for (int j = i + 1; j < threadCount; j++) {
                    assertNotEquals(hashes[i], hashes[j],
                            "Concurrent hashes should all be unique");
                }
            }

            // But all should verify correctly
            for (String hash : hashes) {
                assertTrue(passwordHasher.verifyPassword(password, hash));
            }
        }
    }
}