package server.zookeeper.DB;

import com.google.protobuf.InvalidProtocolBufferException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.zookeeper.proto.auth.UserAuth;
import server.zookeeper.util.EmailUtils;
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

    public void saveOAuthUser(UserAuth user) {
        String email = user.getEmail();
        LOG.debug("Saving user: {}", EmailUtils.maskEmail(email));
        try {
            byte[] key = emailToKey(email);
            byte[] value = user.toByteArray();
            database.put(key, value, AUTH_DIRECTORY);
            LOG.info("Successfully saved user: {}", EmailUtils.maskEmail(email));
        } catch (Exception e) {
            LOG.error("Failed to save user: {}", EmailUtils.maskEmail(email), e);
            throw new RuntimeException(
                    "Failed to save user: " + EmailUtils.maskEmail(email),
                    e);
        }
    }

    public void saveUser(UserAuth user) {
        validateUserAuth(user);
        String email = user.getEmail();
        LOG.debug("Saving user: {}", EmailUtils.maskEmail(email));
        try {
            byte[] key = emailToKey(email);
            byte[] value = user.toByteArray();
            database.put(key, value, AUTH_DIRECTORY);
            LOG.info("Successfully saved user: {}", EmailUtils.maskEmail(email));
        } catch (Exception e) {
            LOG.error("Failed to save user: {}", EmailUtils.maskEmail(email), e);
            throw new RuntimeException(
                    "Failed to save user: " + EmailUtils.maskEmail(email),
                    e);
        }
    }

    public Optional<UserAuth> getUserByEmail(String email) {
        EmailUtils.validateEmail(email);
        LOG.debug("Retrieving user: {}", EmailUtils.maskEmail(email));
        try {
            byte[] key = emailToKey(email);
            byte[] value = database.get(key, AUTH_DIRECTORY);

            if (value == null) {
                LOG.debug("User not found: {}", EmailUtils.maskEmail(email));
                return Optional.empty();
            }

            UserAuth user = UserAuth.parseFrom(value);
            LOG.info("Successfully retrieved user: {}", EmailUtils.maskEmail(email));
            return Optional.of(user);
        } catch (InvalidProtocolBufferException e) {
            LOG.error("Corrupted user data for: {}", EmailUtils.maskEmail(email), e);
            throw new RuntimeException(
                    "Corrupted user data for: " + EmailUtils.maskEmail(email),
                    e);
        } catch (Exception e) {
            LOG.error("Failed to retrieve user: {}", EmailUtils.maskEmail(email), e);
            throw new RuntimeException(
                    "Failed to retrieve user: " + EmailUtils.maskEmail(email),
                    e);
        }
    }

    public boolean userExists(String email) {
        EmailUtils.validateEmail(email);
        LOG.debug("Checking if user exists: {}", EmailUtils.maskEmail(email));
        try {
            byte[] key = emailToKey(email);
            byte[] value = database.get(key, AUTH_DIRECTORY);
            boolean exists = value != null;
            LOG.debug("User {} exists: {}", EmailUtils.maskEmail(email), exists);
            return exists;
        } catch (Exception e) {
            LOG.error("Failed to check user existence: {}", EmailUtils.maskEmail(email), e);
            throw new RuntimeException(
                    "Failed to check user existence: " + EmailUtils.maskEmail(email),
                    e);
        }
    }

    public void deleteUser(String email) {
        EmailUtils.validateEmail(email);
        LOG.debug("Deleting user: {}", EmailUtils.maskEmail(email));
        try {
            byte[] key = emailToKey(email);
            database.delete(key, AUTH_DIRECTORY);
            LOG.info("Successfully deleted user: {}", EmailUtils.maskEmail(email));
        } catch (Exception e) {
            LOG.error("Failed to delete user: {}", EmailUtils.maskEmail(email), e);
            throw new RuntimeException(
                    "Failed to delete user: " + EmailUtils.maskEmail(email),
                    e);
        }
    }

    public void updateUser(UserAuth user) {
        validateUserAuth(user);
        String email = user.getEmail();
        LOG.debug("Updating user: {}", EmailUtils.maskEmail(email));

        if (!userExists(email)) {
            throw new RuntimeException(
                    "Cannot update non-existent user: " + EmailUtils.maskEmail(email));
        }

        try {
            byte[] key = emailToKey(email);
            byte[] value = user.toByteArray();
            database.put(key, value, AUTH_DIRECTORY);
            LOG.info("Successfully updated user: {}", EmailUtils.maskEmail(email));
        } catch (Exception e) {
            LOG.error("Failed to update user: {}", EmailUtils.maskEmail(email), e);
            throw new RuntimeException(
                    "Failed to update user: " + EmailUtils.maskEmail(email),
                    e);
        }
    }

    private void validateUserAuth(UserAuth user) {
        if (user == null) {
            throw new IllegalArgumentException("UserAuth cannot be null");
        }

        EmailUtils.validateEmail(user.getEmail());

        if (user.getPasswordHash() == null || user.getPasswordHash().isEmpty()) {
            throw new IllegalArgumentException("Password hash cannot be null or empty");
        }
    }

    private byte[] emailToKey(String email) {
        return email.toLowerCase().trim().getBytes(StandardCharsets.UTF_8);
    }

}
