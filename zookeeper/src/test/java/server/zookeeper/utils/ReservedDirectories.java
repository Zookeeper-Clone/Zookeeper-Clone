package server.zookeeper.utils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ReservedDirectories {
    public static final String AUTH_DIRECTORY = "__ZK_SYS_AUTH__";

    private static final String SYSTEM_PREFIX = "__ZK_SYS_";
    private static final Set<String> RESERVED_DIRECTORIES;

    static {
        Set<String> reserved = new HashSet<>();
        reserved.add(AUTH_DIRECTORY);
        RESERVED_DIRECTORIES = Collections.unmodifiableSet(reserved);
    }
    private ReservedDirectories() {
        throw new AssertionError("ReservedDirectories is a utility class and should not be instantiated");
    }

    public static boolean isReserved(String directoryName) {
        if (directoryName == null || directoryName.isEmpty()) {
            return false;
        }

        if (RESERVED_DIRECTORIES.contains(directoryName)) {
            return true;
        }

        return directoryName.startsWith(SYSTEM_PREFIX);
    }

    public static Set<String> getReservedDirectories() {
        return RESERVED_DIRECTORIES;
    }

    public static void validateNotReserved(String directoryName) {
        if (isReserved(directoryName)) {
            throw new IllegalArgumentException(
                    String.format("Directory '%s' is reserved for system use and cannot be accessed directly",
                            directoryName)
            );
        }
    }

    public static String getReservedDirectoryError(String directoryName) {
        return String.format(
                "ERROR: Directory '%s' is reserved for system use. " +
                        "System directories with prefix '%s' cannot be accessed directly.",
                directoryName, SYSTEM_PREFIX
        );
    }
}
