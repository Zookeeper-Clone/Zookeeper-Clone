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
 * Examples:
 * - READ_ONLY (2): Can only read existing entries.
 * - MODIFY_ONLY (6 = READ | UPDATE): Can read and update existing entries, but cannot add new entries or delete.
 * - READ_WRITE (7 = CREATE | READ | UPDATE): Can create, read, and update, but cannot delete.
 * - FULL_ACCESS (15 = CREATE | READ | UPDATE | DELETE): Full CRUD access.
 */
public final class PermissionConstants {
    
    private PermissionConstants() {
        // Prevent instantiation
    }
    
    public static final int NONE = 0;
    public static final int CREATE = 1;
    public static final int READ = 2;
    public static final int UPDATE = 4;
    public static final int DELETE = 8;
    
    /** Read-only access */
    public static final int READ_ONLY = READ;
    
    /** Read and modify existing entries without adding new entries or deleting */
    public static final int MODIFY_ONLY = READ | UPDATE;
    
    /** Read and create without modifying existing or deleting */
    public static final int CREATE_AND_READ = CREATE | READ;
    
    /** Create, read, and update without delete */
    public static final int READ_WRITE = CREATE | READ | UPDATE;
    
    /** Full CRUD access */
    public static final int FULL_ACCESS = CREATE | READ | UPDATE | DELETE;

    /**
     * Checks if a permission mask contains only valid permission bits (0 to FULL_ACCESS).
     * 
     * @param mask the bitmask to validate
     * @return true if the mask is non-negative and contains no bits outside FULL_ACCESS
     */
    public static boolean isValid(int mask) {
        return mask >= 0 && (mask & ~FULL_ACCESS) == 0;
    }

    /**
     * Check if a permission bitmask contains the required permission.
     * 
     * @param permissionMask the user's permission bitmask for a directory
     * @param requiredPermission the required permission (CREATE, READ, UPDATE, or DELETE)
     * @return true if the user has the required permission
     */
    public static boolean hasPermission(int permissionMask, int requiredPermission) {
        if (!isValid(permissionMask) || !isValid(requiredPermission) || requiredPermission == NONE) {
            return false;
        }
        return (permissionMask & requiredPermission) == requiredPermission;
    }

    public static boolean canRead(int permissionMask) {
        return hasPermission(permissionMask, READ);
    }

    public static boolean canCreate(int permissionMask) {
        return hasPermission(permissionMask, CREATE);
    }

    public static boolean canUpdate(int permissionMask) {
        return hasPermission(permissionMask, UPDATE);
    }

    public static boolean canDelete(int permissionMask) {
        return hasPermission(permissionMask, DELETE);
    }

    /**
     * Combine boolean flags into a permission bitmask.
     */
    public static int of(boolean create, boolean read, boolean update, boolean delete) {
        int mask = NONE;
        if (create) mask |= CREATE;
        if (read)   mask |= READ;
        if (update) mask |= UPDATE;
        if (delete) mask |= DELETE;
        return mask;
    }
}
