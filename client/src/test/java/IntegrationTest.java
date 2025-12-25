import static org.junit.jupiter.api.Assertions.*;

import org.apache.ratis.client.RaftClient;
import org.junit.jupiter.api.*;

import client.zookeeper.RaftClientBuilder;
import client.zookeeper.ZookeeperClient;

import java.util.UUID;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class IntegrationTest {

    private static RaftClient raftClient;
    private static ZookeeperClient client;
    private static final String notFound = "__NOT_FOUND__";
    private static final String[] IDS = { "n1", "n2", "n3", "n4", "n5" };
    private static final int[] PORTS = { 6001, 6002, 6003, 6004, 6005 };
    private static final String GROUP_ID = "00000000-0000-0000-0000-000000000001";
    private static final String NAMESPACE = UUID.randomUUID().toString();

    @BeforeAll
    public static void setUp() {
        raftClient = new RaftClientBuilder()
                .setPeers(IDS, PORTS)
                .setGroupId(GROUP_ID)
                .build();
        client = new ZookeeperClient(raftClient, event -> {});
        client.register("test@user.com", "user12345");
        client.login("test@user.com", "user12345");
    }

    @AfterAll
    public static void tearDown() throws Exception {
        if (raftClient != null) {
            raftClient.close();
        }
    }

    private static String namespaced(String key) {
        return NAMESPACE + "::" + key;
    }

    @Test
    public void testBasicCreateAndRead() {
        String key = namespaced("user1");
        ZookeeperClient.QueryResult createResult = client.create(key, "John Doe", false);
        assertTrue(createResult.isSuccess(), "Create operation should succeed");

        String readResponse = client.read(key).getValue();
        assertEquals("John Doe", readResponse, "Read should return the written value");
    }

    @Test
    public void testMultipleCreates() {
        String key1 = namespaced("user2");
        String key2 = namespaced("user3");
        String key3 = namespaced("user4");

        assertTrue(client.create(key1, "Alice", false).isSuccess());
        assertTrue(client.create(key2, "Bob", false).isSuccess());
        assertTrue(client.create(key3, "Charlie", false).isSuccess());

        assertEquals("Alice", client.read(key1).getValue());
        assertEquals("Bob", client.read(key2).getValue());
        assertEquals("Charlie", client.read(key3).getValue());
    }

    @Test
    public void testUpdateExistingKey() {
        String key = namespaced("config");
        assertTrue(client.create(key, "version1", false).isSuccess());
        assertEquals("version1", client.read(key).getValue());

        assertTrue(client.update(key, "version2").isSuccess(), "Update operation should succeed");

        assertEquals("version2", client.read(key).getValue());
    }

    @Test
    public void testCreateAndReadWithDirectory() {
        String key = namespaced("employee1");
        assertTrue(client.create(key, "Engineering", "department", false).isSuccess());
        assertTrue(client.create(key, "$80000", "salary", false).isSuccess());
        assertTrue(client.create(key, "Senior Developer", "position", false).isSuccess());

        assertEquals("Engineering", client.read(key, "department").getValue());
        assertEquals("$80000", client.read(key, "salary").getValue());
        assertEquals("Senior Developer", client.read(key, "position").getValue());
    }

    @Test
    public void testMultipleEntriesWithDirectories() {
        String key1 = namespaced("product1");
        String key2 = namespaced("product2");

        assertTrue(client.create(key1, "Laptop", "name", false).isSuccess());
        assertTrue(client.create(key1, "$1200", "price", false).isSuccess());
        assertTrue(client.create(key2, "Mouse", "name", false).isSuccess());
        assertTrue(client.create(key2, "$25", "price", false).isSuccess());

        assertEquals("Laptop", client.read(key1, "name").getValue());
        assertEquals("$1200", client.read(key1, "price").getValue());
        assertEquals("Mouse", client.read(key2, "name").getValue());
        assertEquals("$25", client.read(key2, "price").getValue());
    }

    @Test
    public void testDeleteBasicEntry() {
        String key = namespaced("temp");
        assertTrue(client.create(key, "temporary data", false).isSuccess());
        assertEquals("temporary data", client.read(key).getValue());

        assertTrue(client.delete(key).isSuccess());

        String readAfterDelete = client.read(key).getValue();
        assertEquals(notFound, readAfterDelete, "Deleted entry should not be found");
    }

    @Test
    public void testDeleteEntryWithDirectory() {
        String key = namespaced("session1");
        assertTrue(client.create(key, "active", "status", false).isSuccess());
        assertEquals("active", client.read(key, "status").getValue());

        assertTrue(client.delete(key, "status").isSuccess());

        String readAfterDelete = client.read(key, "status").getValue();
        assertEquals(notFound, readAfterDelete, "Deleted entry should not be found");
    }

    @Test
    public void testReadNonExistentKey() {
        String result = client.read(namespaced("nonexistent_key")).getValue();
        assertEquals(notFound, result, "Non-existent key should return __NOT_FOUND__");
    }

    @Test
    public void testReadNonExistentDirectory() {
        String result = client.read(namespaced("some_key"), "nonexistent_directory").getValue();
        assertEquals(notFound, result, "Non-existent directory should return __NOT_FOUND__");
    }

    @Test
    public void testComplexScenario() {
        String key = namespaced("app_config");
        assertTrue(client.create(key, "production", "environment", false).isSuccess());
        assertTrue(client.create(key, "true", "debug_mode", false).isSuccess());
        assertTrue(client.create(key, "INFO", "log_level", false).isSuccess());

        assertEquals("production", client.read(key, "environment").getValue());
        assertEquals("true", client.read(key, "debug_mode").getValue());
        assertEquals("INFO", client.read(key, "log_level").getValue());

        assertTrue(client.update(key, "false", "debug_mode").isSuccess());
        assertEquals("false", client.read(key, "debug_mode").getValue());

        assertTrue(client.delete(key, "log_level").isSuccess());
        assertEquals(notFound, client.read(key, "log_level").getValue());

        assertEquals("production", client.read(key, "environment").getValue());
        assertEquals("false", client.read(key, "debug_mode").getValue());
    }

    @Test
    public void testSpecialCharactersInValues() {
        String messageKey = namespaced("message");
        assertTrue(client.create(messageKey, "Hello, World! @#$%^&*()", false).isSuccess());
        assertEquals("Hello, World! @#$%^&*()", client.read(messageKey).getValue());

        String jsonKey = namespaced("json_like");
        assertTrue(client.create(jsonKey, "{\"key\":\"value\"}", false).isSuccess());
        assertEquals("{\"key\":\"value\"}", client.read(jsonKey).getValue());
    }

    @Test
    public void testLargeValueStorage() {
        StringBuilder largeValue = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            largeValue.append("This is line ").append(i).append(".");
        }

        String value = largeValue.toString();
        String key = namespaced("large_data");
        assertTrue(client.create(key, value, false).isSuccess());
        assertEquals(value, client.read(key).getValue());
    }

    @Test
    public void testSequentialOperations() {
        String key = namespaced("counter");

        assertTrue(client.create(key, "0", false).isSuccess());
        assertEquals("0", client.read(key).getValue());
        assertTrue(client.update(key, "1").isSuccess());
        assertEquals("1", client.read(key).getValue());
        assertTrue(client.update(key, "2").isSuccess());
        assertEquals("2", client.read(key).getValue());
        assertTrue(client.update(key, "3").isSuccess());
        assertEquals("3", client.read(key).getValue());
    }

    @Test
    public void testMixedBasicAndDirectoryOperations() {
        String key = namespaced("mixed_key");

        assertTrue(client.create(key, "base_value", false).isSuccess());
        assertEquals("base_value", client.read(key).getValue());

        assertTrue(client.create(key, "dir_value1", "dir1", false).isSuccess());
        assertTrue(client.create(key, "dir_value2", "dir2", false).isSuccess());

        assertEquals("base_value", client.read(key).getValue());
        assertEquals("dir_value1", client.read(key, "dir1").getValue());
        assertEquals("dir_value2", client.read(key, "dir2").getValue());
    }

    @Test
    public void testDeleteAndRewrite() {
        String key = namespaced("rewritable");

        assertTrue(client.create(key, "first_value", false).isSuccess());
        assertEquals("first_value", client.read(key).getValue());

        assertTrue(client.delete(key).isSuccess());
        assertEquals(notFound, client.read(key).getValue());

        assertTrue(client.create(key, "second_value", false).isSuccess());
        assertEquals("second_value", client.read(key).getValue());
    }

    @Test
    public void testMultipleDirectoriesPerKey() {
        String key = namespaced("user_profile");

        assertTrue(client.create(key, "Csed", "firstname", false).isSuccess());
        assertTrue(client.create(key, "Doe", "lastname", false).isSuccess());
        assertTrue(client.create(key, "csed@zookeeper.com", "email", false).isSuccess());
        assertTrue(client.create(key, "30", "age", false).isSuccess());
        assertTrue(client.create(key, "New York", "city", false).isSuccess());

        assertEquals("Csed", client.read(key, "firstname").getValue());
        assertEquals("Doe", client.read(key, "lastname").getValue());
        assertEquals("csed@zookeeper.com", client.read(key, "email").getValue());
        assertEquals("30", client.read(key, "age").getValue());
        assertEquals("New York", client.read(key, "city").getValue());
    }

    @Test
    public void testEphemeralEntryExpiration() throws Exception {
        // ! Create a separate client for ephemeral session
        RaftClient ephemeralRaftClient = new RaftClientBuilder()
                .setPeers(IDS, PORTS)
                .setGroupId(GROUP_ID)
                .build();

        try (ZookeeperClient ephemeralClient = new ZookeeperClient(ephemeralRaftClient, event -> {})) {
            ephemeralClient.register("ephemeral@user.com", "pass1234");
            ephemeralClient.login("ephemeral@user.com", "pass1234");
            String key = namespaced("ephemeralKey");
            String value = "temporary";

            assertTrue(ephemeralClient.create(key, value, true).isSuccess());
            assertEquals(value, ephemeralClient.read(key).getValue());

            ephemeralClient.logout();
            String readValue = client.read(key).getValue();
            assertEquals(notFound, readValue, "Ephemeral entry should be removed after session timeout");
        }
    }

}
