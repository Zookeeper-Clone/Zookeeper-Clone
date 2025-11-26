//package server.zookeeper.util;
//
//public class EnvUtils {
//    public static String getRequiredEnv(String key) {
//        String value = System.getenv(key);
//        if (value == null || value.isEmpty()) {
//            throw new IllegalStateException(key + " must be set");
//        }
//        return value;
//    }
//}

package server.zookeeper.util;

public class EnvUtils {
    public static String getRequiredEnv(String key) {
        // First check environment variable
        String value = System.getenv(key);

        // Fallback to system property
        if (value == null) {
            value = System.getProperty(key);
        }

        if (value == null) {
            throw new IllegalStateException(key + " must be set");
        }
        return value;
    }
}
