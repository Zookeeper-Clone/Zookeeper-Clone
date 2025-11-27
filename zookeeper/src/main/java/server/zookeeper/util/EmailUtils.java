package server.zookeeper.util;

import java.util.regex.Pattern;

public final class EmailUtils {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private EmailUtils() {
        throw new AssertionError("EmailUtils is a utility class and should not be instantiated");
    }

    /**
     * Masks email for secure logging (shows first 2 chars and domain).
     *
     * Examples:
     * - "john.doe@example.com" -> "jo***@example.com"
     *
     * @param email the email to mask
     * @return masked email string
     */
    public static String maskEmail(String email) {
        if (email == null || email.length() < 3) {
            return "***";
        }

        int atIndex = email.indexOf('@');
        if (atIndex < 0) {
            // No @ symbol, mask everything except first 2 chars
            return email.substring(0, Math.min(2, email.length())) + "***";
        }

        // Show first 2 chars of local part and full domain
        return email.substring(0, Math.min(2, atIndex)) + "***" + email.substring(atIndex);
    }

    public static void validateEmail(String email) {
        // Check null or empty
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }

        String trimmedEmail = email.trim();

        if (trimmedEmail.length() < 3 || trimmedEmail.length() > 254) {
            throw new IllegalArgumentException(
                    "Email length must be between 3 and 254 characters: " + EmailUtils.maskEmail(email)
            );
        }

        if (!EMAIL_PATTERN.matcher(trimmedEmail).matches()) {
            throw new IllegalArgumentException("Invalid email format: " + EmailUtils.maskEmail(email));
        }
    }
}
