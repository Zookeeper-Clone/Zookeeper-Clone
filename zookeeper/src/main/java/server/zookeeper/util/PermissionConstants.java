package server.zookeeper.util;

/**
 * Permission bitmask constants for directory access control.
 * 
 * Permissions are stored as an integer bitmask where:
 * - CREATE = 1 (0001)
 * - READ   = 2 (0010)
 * - UPDATE = 4 (0100)
 * - DELETE = 8 (1000)
 * 
 * Example: A user with permission value 15 has full CRUD access (1+2+4+8=15)
 */
public final class PermissionConstants {
    
    private PermissionConstants() {
        // Prevent instantiation
    }
    
    public static final int CREATE = 1;
    public static final int READ = 2;
    public static final int UPDATE = 4;
    public static final int DELETE = 8;
    
    /** Full CRUD access */
    public static final int FULL_ACCESS = CREATE | READ | UPDATE | DELETE;
    
    /**
     * Check if a permission bitmask contains the required permission.
     * 
     * @param permissionMask the user's permission bitmask for a directory
     * @param requiredPermission the required permission (CREATE, READ, UPDATE, or DELETE)
     * @return true if the user has the required permission
     */
    public static boolean hasPermission(int permissionMask, int requiredPermission) {
        return (permissionMask & requiredPermission) == requiredPermission;
    }
}
