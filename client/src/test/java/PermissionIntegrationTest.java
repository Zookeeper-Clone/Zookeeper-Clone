// java
import static org.junit.jupiter.api.Assertions.*;

import org.apache.ratis.client.RaftClient;
import org.junit.jupiter.api.*;

import client.zookeeper.PermissionConstants;
import client.zookeeper.RaftClientBuilder;
import client.zookeeper.ZookeeperClient;

import java.util.HashMap;
import java.util.Map;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PermissionIntegrationTest {

    private static RaftClient raftClient;
    private static ZookeeperClient client;
    private static final String GROUP_ID = "00000000-0000-0000-0000-000000000001";
    private static final String[] IDS = {"n1", "n2", "n3", "n4", "n5"};
    private static final int[] PORTS = {6001, 6002, 6003, 6004, 6005};

    @BeforeAll
    public static void setUp() {
        raftClient = new RaftClientBuilder()
                .setPeers(IDS, PORTS)
                .setGroupId(GROUP_ID)
                .build();
        client = new ZookeeperClient(raftClient, event -> {});

        // Register and login initial admin user first (receives automatic admin privileges)
        client.register("perm_admin@user.com", "adminpass1");
        client.login("perm_admin@user.com", "adminpass1");

        // Register target users that will have permissions updated
        client.register("alice@example.com", "alicepwd12");
        client.register("bob@example.com", "bobpwd123");
    }

    @AfterAll
    public static void tearDown() throws Exception {
        if (raftClient != null) {
            raftClient.close();
        }
    }

    @Test
    @Order(1)
    public void testSetAndGetIsAdmin() {
        ZookeeperClient.PermissionsResult setAdmin = client.setIsAdmin("alice@example.com", true);
        assertTrue(setAdmin.isSuccess(), "Setting isAdmin should succeed");

        ZookeeperClient.PermissionsResult getAdmin = client.getUserPermissionsByEmail("alice@example.com");
        assertTrue(getAdmin.isSuccess(), "Getting permissions should succeed");
        assertNotNull(getAdmin.getUserPermissions(), "UserPermissions must be present");
        assertTrue(getAdmin.getUserPermissions().getIsAdmin(), "alice should be admin");
    }

    @Test
    @Order(2)
    public void testSetAndGetCanCreateDirectories() {
        ZookeeperClient.PermissionsResult setCreate = client.setCanCreateDirectories("bob@example.com", true);
        assertTrue(setCreate.isSuccess(), "Setting canCreateDirectories should succeed");

        ZookeeperClient.PermissionsResult getBob = client.getUserPermissionsByEmail("bob@example.com");
        assertTrue(getBob.isSuccess(), "Getting bob's permissions should succeed");
        assertNotNull(getBob.getUserPermissions());
        assertTrue(getBob.getUserPermissions().getCanCreateDirectories(), "bob should be allowed to create directories");
    }

    @Test
    @Order(3)
    public void testSetAndGetDirectoryPermissions() {
        Map<String, Integer> perms = new HashMap<>();
        perms.put("finance", 7);
        perms.put("hr", 3);

        ZookeeperClient.PermissionsResult setDirs = client.setDirectoryPermissions("alice@example.com", perms);
        assertTrue(setDirs.isSuccess(), "Setting directory permissions should succeed");

        ZookeeperClient.PermissionsResult getAlice = client.getUserPermissionsByEmail("alice@example.com");
        assertTrue(getAlice.isSuccess());
        assertNotNull(getAlice.getUserPermissions());

        Map<String, Integer> returned = getAlice.getUserPermissions().getDirectoryPermissionsMap();
        assertEquals(2, returned.size());
        assertEquals(Integer.valueOf(7), returned.get("finance"));
        assertEquals(Integer.valueOf(3), returned.get("hr"));
    }

    @Test
    @Order(4)
    public void testSetGranularCRUDPermissions() {
        // Assign bob READ_ONLY (2) on 'reports' and MODIFY_ONLY (6 = READ | UPDATE) on 'logs'
        client.setDirectoryPermission("bob@example.com", "reports", PermissionConstants.READ_ONLY);
        client.setDirectoryPermission("bob@example.com", "logs", PermissionConstants.MODIFY_ONLY);

        ZookeeperClient.PermissionsResult getBob = client.getUserPermissionsByEmail("bob@example.com");
        assertTrue(getBob.isSuccess());
        assertNotNull(getBob.getUserPermissions());

        Map<String, Integer> returned = getBob.getUserPermissions().getDirectoryPermissionsMap();
        assertEquals(Integer.valueOf(PermissionConstants.READ_ONLY), returned.get("reports"));
        assertEquals(Integer.valueOf(PermissionConstants.MODIFY_ONLY), returned.get("logs"));
        assertTrue(PermissionConstants.canRead(returned.get("reports")));
        assertFalse(PermissionConstants.canCreate(returned.get("reports")));
        assertFalse(PermissionConstants.canUpdate(returned.get("reports")));

        assertTrue(PermissionConstants.canRead(returned.get("logs")));
        assertTrue(PermissionConstants.canUpdate(returned.get("logs")));
        assertFalse(PermissionConstants.canCreate(returned.get("logs")));
        assertFalse(PermissionConstants.canDelete(returned.get("logs")));
    }

    @Test
    @Order(5)
    public void testGetNonExistentUserPermissionsFails() {
        ZookeeperClient.PermissionsResult res = client.getUserPermissionsByEmail("noone@nowhere.com");
        assertFalse(res.isSuccess(), "Request for non-existent user should fail");
        assertNull(res.getUserPermissions(), "No permissions should be returned for missing user");
    }

    @Test
    @Order(6)
    public void testNonAdminCannotModifyPermissions() {
        RaftClient userRaftClient = new RaftClientBuilder()
                .setPeers(IDS, PORTS)
                .setGroupId(GROUP_ID)
                .build();
        try (ZookeeperClient userClient = new ZookeeperClient(userRaftClient, event -> {})) {
            userClient.login("bob@example.com", "bobpwd123");
            // Bob is not admin, so attempting to set admin on alice should fail
            ZookeeperClient.PermissionsResult failAdmin = userClient.setIsAdmin("alice@example.com", false);
            assertFalse(failAdmin.isSuccess(), "Non-admin should not be able to set isAdmin");

            // Bob attempting to view alice's permissions should fail
            ZookeeperClient.PermissionsResult failGet = userClient.getUserPermissionsByEmail("alice@example.com");
            assertFalse(failGet.isSuccess(), "Non-admin should not be able to view another user's permissions");

            // Bob viewing his own permissions should succeed
            ZookeeperClient.PermissionsResult ownPerms = userClient.getUserPermissionsByEmail("bob@example.com");
            assertTrue(ownPerms.isSuccess(), "User should be able to view their own permissions");
        } catch (Exception e) {
            fail("Exception during non-admin permission test: " + e.getMessage());
        }
    }
}
