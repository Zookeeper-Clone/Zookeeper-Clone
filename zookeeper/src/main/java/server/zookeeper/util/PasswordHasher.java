package server.zookeeper.util;

import at.favre.lib.crypto.bcrypt.BCrypt;
import at.favre.lib.crypto.bcrypt.LongPasswordStrategies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class PasswordHasher {
    private static final Logger LOG = LoggerFactory.getLogger(PasswordHasher.class);
    private static final int DEFAULT_COST_FACTOR = 14;
    private static final int MIN_COST_FACTOR = 4;
    private static final int MAX_COST_FACTOR = 31;
    private static final String COST_FACTOR_ENV = "BCRYPT_COST_FACTOR";
    private static volatile PasswordHasher instance;
    private final int costFactor;
    private final BCrypt.Hasher hasher;
    private final BCrypt.Verifyer verifyer;

    private PasswordHasher() {
        this.costFactor = loadCostFactorFromEnvironment();

        // Use strict UTF-8 mode and SHA-512 for long passwords (>72 bytes)
        this.hasher = BCrypt.with(
                BCrypt.Version.VERSION_2A,
                LongPasswordStrategies.hashSha512(BCrypt.Version.VERSION_2A)
        );

        this.verifyer = BCrypt.verifyer(
                BCrypt.Version.VERSION_2A,
                LongPasswordStrategies.hashSha512(BCrypt.Version.VERSION_2A)
        );

        LOG.info("PasswordHasher initialized with cost factor: {}", costFactor);
    }

    public static PasswordHasher getInstance() {
        if (instance == null) {
            synchronized (PasswordHasher.class) {
                if (instance == null) {
                    instance = new PasswordHasher();
                }
            }
        }
        return instance;
    }

    public String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }

        try {
            long startTime = System.currentTimeMillis();
            String hash = hasher.hashToString(costFactor, plainPassword.toCharArray());
            long duration = System.currentTimeMillis() - startTime;
            LOG.debug("Password hashed in {}ms with cost factor {}", duration, costFactor);
            return hash;
        } catch (Exception e) {
            LOG.error("Failed to hash password", e);
            throw new RuntimeException("Password hashing failed: " + e.getMessage(), e);
        }
    }

    public boolean verifyPassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Plain password cannot be null or empty");
        }

        if (hashedPassword == null || hashedPassword.isEmpty()) {
            LOG.warn("Attempted verification with null/empty hash");
            return false;
        }

        try {
            long startTime = System.currentTimeMillis();
            BCrypt.Result result = verifyer.verify(
                    plainPassword.toCharArray(),
                    hashedPassword
            );
            long duration = System.currentTimeMillis() - startTime;
            boolean isValid = result.verified;
            LOG.debug("Password verification completed in {}ms, result: {}", duration, isValid);

            // Check if hash format is outdated
            if (isValid && result.details != null) {
                int hashCost = result.details.cost;
                if (hashCost < costFactor) {
                    LOG.warn("Password hash uses outdated cost factor {} (current: {}). Consider rehashing.",
                            hashCost, costFactor);
                }
            }
            return isValid;
        } catch (Exception e) {
            LOG.error("Password verification failed", e);
            return false;
        }
    }

    private int loadCostFactorFromEnvironment() {
        String envValue = System.getenv(COST_FACTOR_ENV);

        if (envValue == null || envValue.isEmpty()) {
            LOG.debug("No cost factor configured, using default: {}", DEFAULT_COST_FACTOR);
            return DEFAULT_COST_FACTOR;
        }

        try {
            int cost = Integer.parseInt(envValue);

            if (cost < MIN_COST_FACTOR) {
                LOG.warn("Cost factor {} below minimum {}, using minimum",
                        cost, MIN_COST_FACTOR);
                return MIN_COST_FACTOR;
            }

            if (cost > MAX_COST_FACTOR) {
                LOG.warn("Cost factor {} above maximum {}, using maximum",
                        cost, MAX_COST_FACTOR);
                return MAX_COST_FACTOR;
            }

            LOG.info("Using configured cost factor: {}", cost);
            return cost;

        } catch (NumberFormatException e) {
            LOG.warn("Invalid cost factor '{}', using default: {}",
                    envValue, DEFAULT_COST_FACTOR);
            return DEFAULT_COST_FACTOR;
        }
    }

    public int getCostFactor() {
        return costFactor;
    }

    /**
     * Factory method to create PasswordHasher with custom cost factor.
     *
     * <p><strong>Warning:</strong> This bypasses the singleton pattern.
     * Only use for testing purposes.</p>
     */
    public static PasswordHasher createForTesting(int costFactor) {
        if (costFactor < MIN_COST_FACTOR || costFactor > MAX_COST_FACTOR) {
            throw new IllegalArgumentException(
                    String.format("Cost factor must be between %d and %d",
                            MIN_COST_FACTOR, MAX_COST_FACTOR)
            );
        }

        PasswordHasher hasher = new PasswordHasher();
        try {
            java.lang.reflect.Field field = PasswordHasher.class.getDeclaredField("costFactor");
            field.setAccessible(true);
            field.set(hasher, costFactor);
            return hasher;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test instance", e);
        }
    }
}
