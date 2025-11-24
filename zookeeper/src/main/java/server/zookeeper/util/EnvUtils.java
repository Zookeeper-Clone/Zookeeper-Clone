package server.zookeeper.util;

public class EnvUtils {
    public static String getRequiredEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException(key + " must be set");
        }
        return value;
    }
}
