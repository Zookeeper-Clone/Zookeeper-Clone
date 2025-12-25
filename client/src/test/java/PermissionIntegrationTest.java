// java
import static org.junit.jupiter.api.Assertions.*;

import org.apache.ratis.client.RaftClient;
import org.junit.jupiter.api.*;

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

//         register target users that will have permissions updated
        client.register("alice@example.com", "alicepwd12");
        client.register("bob@example.com", "bobpwd123");

        // create and login an admin user to perform permission changes
        client.register("perm_admin@user.com", "adminpass1");
        client.login("perm_admin@user.com", "adminpass1");
        // Grant admin permissions to manage other users' permissions
        client.setIsAdmin("perm_admin@user.com", true);


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
    public void testGetNonExistentUserPermissionsFails() {
        ZookeeperClient.PermissionsResult res = client.getUserPermissionsByEmail("noone@nowhere.com");
        assertFalse(res.isSuccess(), "Request for non-existent user should fail");
        assertNull(res.getUserPermissions(), "No permissions should be returned for missing user");
    }
}
