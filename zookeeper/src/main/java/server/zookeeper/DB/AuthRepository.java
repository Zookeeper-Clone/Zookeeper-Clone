package server.zookeeper.DB;

import com.google.protobuf.InvalidProtocolBufferException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.zookeeper.proto.auth.UserAuth;
import server.zookeeper.util.ReservedDirectories;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class AuthRepository {
    private static final Logger LOG = LoggerFactory.getLogger(AuthRepository.class);
    private static final String AUTH_DIRECTORY = ReservedDirectories.AUTH_DIRECTORY;
    private final DataBase database;

    public AuthRepository(DataBase database) {
        if (database == null) {
            throw new IllegalArgumentException("Database cannot be null");
        }
        this.database = database;
        LOG.info("AuthRepository initialized with column family: {}", AUTH_DIRECTORY);
    }

    public void saveUser(UserAuth user) {
        validateUserAuth(user);
        String email = user.getEmail();
        LOG.debug("Saving user: {}", maskEmail(email));
        try {
            byte[] key = emailToKey(email);
            byte[] value = user.toByteArray();
            database.put(key, value, AUTH_DIRECTORY);
            LOG.info("Successfully saved user: {}", maskEmail(email));
        } catch (Exception e) {
            LOG.error("Failed to save user: {}", maskEmail(email), e);
            throw new RuntimeException(
                    "Failed to save user: " + maskEmail(email),
                    e
            );
        }
    }

    public Optional<UserAuth> getUserByEmail(String email) {
        validateEmail(email);
        LOG.debug("Retrieving user: {}", maskEmail(email));
        try {
            byte[] key = emailToKey(email);
            byte[] value = database.get(key, AUTH_DIRECTORY);

            if (value == null) {
                LOG.debug("User not found: {}", maskEmail(email));
                return Optional.empty();
            }

            UserAuth user = UserAuth.parseFrom(value);
            LOG.debug("Successfully retrieved user: {}", maskEmail(email));
            return Optional.of(user);
        } catch (InvalidProtocolBufferException e) {
            LOG.error("Corrupted user data for: {}", maskEmail(email), e);
            throw new RuntimeException(
                    "Corrupted user data for: " + maskEmail(email),
                    e
            );
        } catch (Exception e) {
            LOG.error("Failed to retrieve user: {}", maskEmail(email), e);
            throw new RuntimeException(
                    "Failed to retrieve user: " + maskEmail(email),
                    e
            );
        }
    }

    public boolean userExists(String email) {
        validateEmail(email);
        LOG.debug("Checking if user exists: {}", maskEmail(email));
        try {
            byte[] key = emailToKey(email);
            byte[] value = database.get(key, AUTH_DIRECTORY);
            boolean exists = value != null;
            LOG.debug("User {} exists: {}", maskEmail(email), exists);
            return exists;
        } catch (Exception e) {
            LOG.error("Failed to check user existence: {}", maskEmail(email), e);
            throw new RuntimeException(
                    "Failed to check user existence: " + maskEmail(email),
                    e
            );
        }
    }

    public void deleteUser(String email) {
        validateEmail(email);
        LOG.debug("Deleting user: {}", maskEmail(email));
        try {
            byte[] key = emailToKey(email);
            database.delete(key, AUTH_DIRECTORY);
            LOG.info("Successfully deleted user: {}", maskEmail(email));
        } catch (Exception e) {
            LOG.error("Failed to delete user: {}", maskEmail(email), e);
            throw new RuntimeException(
                    "Failed to delete user: " + maskEmail(email),
                    e
            );
        }
    }

    public void updateUser(UserAuth user) {
        validateUserAuth(user);
        String email = user.getEmail();
        LOG.debug("Updating user: {}", maskEmail(email));

        if (!userExists(email)) {
            throw new RuntimeException(
                    "Cannot update non-existent user: " + maskEmail(email)
            );
        }

        try {
            byte[] key = emailToKey(email);
            byte[] value = user.toByteArray();
            database.put(key, value, AUTH_DIRECTORY);
            LOG.info("Successfully updated user: {}", maskEmail(email));
        } catch (Exception e) {
            LOG.error("Failed to update user: {}", maskEmail(email), e);
            throw new RuntimeException(
                    "Failed to update user: " + maskEmail(email),
                    e
            );
        }
    }

    private void validateUserAuth(UserAuth user) {
        if (user == null) {
            throw new IllegalArgumentException("UserAuth cannot be null");
        }

        validateEmail(user.getEmail());

        if (user.getPasswordHash() == null || user.getPasswordHash().isEmpty()) {
            throw new IllegalArgumentException("Password hash cannot be null or empty");
        }
    }

    private void validateEmail(String email) {
        // Check null or empty
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }

        String trimmedEmail = email.trim();

        if (trimmedEmail.length() < 3 || trimmedEmail.length() > 254) {
            throw new IllegalArgumentException(
                    "Email length must be between 3 and 254 characters: " + maskEmail(email)
            );
        }

        // Check for whitespace
        if (trimmedEmail.contains(" ") || trimmedEmail.contains("\t") || trimmedEmail.contains("\n")) {
            throw new IllegalArgumentException(
                    "Email cannot contain whitespace: " + maskEmail(email)
            );
        }

        // Check @ symbol (exactly one)
        int atIndex = trimmedEmail.indexOf('@');
        int lastAtIndex = trimmedEmail.lastIndexOf('@');

        if (atIndex == -1) {
            throw new IllegalArgumentException(
                    "Email must contain @ symbol: " + maskEmail(email)
            );
        }

        if (atIndex != lastAtIndex) {
            throw new IllegalArgumentException(
                    "Email cannot contain multiple @ symbols: " + maskEmail(email)
            );
        }

        // Check domain part (after @)
        String domain = trimmedEmail.substring(atIndex + 1);
        // Check domain has at least one dot
        int lastDotIndex = domain.lastIndexOf('.');
        if (lastDotIndex == -1) {
            throw new IllegalArgumentException(
                    "Email domain must contain a dot: " + maskEmail(email)
            );
        }

        // Check no consecutive dots
        if (trimmedEmail.contains("..")) {
            throw new IllegalArgumentException(
                    "Email cannot contain consecutive dots: " + maskEmail(email)
            );
        }
    }

    private byte[] emailToKey(String email) {
        return email.toLowerCase().trim().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Masks email for secure logging (shows first 2 chars and domain).
     *
     * @param email the email to mask
     * @return masked email string
     */
    private String maskEmail(String email) {
        if (email == null || email.length() < 3) {
            return "***";
        }

        int atIndex = email.indexOf('@');
        if (atIndex < 0) {
            return email.substring(0, Math.min(2, email.length())) + "***";
        }

        return email.substring(0, Math.min(2, atIndex)) + "***" + email.substring(atIndex);
    }
}
