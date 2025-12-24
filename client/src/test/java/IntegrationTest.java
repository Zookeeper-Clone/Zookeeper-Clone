import static org.junit.jupiter.api.Assertions.*;

import org.apache.ratis.client.RaftClient;
import org.junit.jupiter.api.*;

import client.zookeeper.RaftClientBuilder;
import client.zookeeper.ZookeeperClient;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class IntegrationTest {

    private static RaftClient raftClient;
    private static ZookeeperClient client;
    private static final String entrySuccess = "OK ENTRY ADDED";
    private static final String notFound = "__NOT_FOUND__";
    private static final String[] IDS = {"n1", "n2", "n3", "n4", "n5"};
    private static final int[] PORTS = {6001, 6002, 6003, 6004, 6005};
    private static final String GROUP_ID = "00000000-0000-0000-0000-000000000001";

    @BeforeAll
    public static void setUp() {
        raftClient = new RaftClientBuilder()
                .setPeers(IDS, PORTS)
                .setGroupId(GROUP_ID)
                .build();
        client = new ZookeeperClient(raftClient);
        client.register("test@user.com", "user12345");
        client.login("test@user.com", "user12345");
    }

    @AfterAll
    public static void tearDown() throws Exception {
        if (raftClient != null) {
            raftClient.close();
        }
    }

    @Test
    public void testBasicWriteAndRead() {
        String writeResponse = client.write("user1", "John Doe", false).getValue();
        assertEquals(entrySuccess, writeResponse, "Write operation should succeed");

        String readResponse = client.read("user1").getValue();
        assertEquals("John Doe", readResponse, "Read should return the written value");
    }

    @Test
    public void testMultipleWrites() {
        String response1 = client.write("user2", "Alice", false).getValue();
        String response2 = client.write("user3", "Bob", false).getValue();
        String response3 = client.write("user4", "Charlie", false).getValue();

        assertEquals(entrySuccess, response1);
        assertEquals(entrySuccess, response2);
        assertEquals(entrySuccess, response3);

        assertEquals("Alice", client.read("user2").getValue());
        assertEquals("Bob", client.read("user3").getValue());
        assertEquals("Charlie", client.read("user4").getValue());
    }

    @Test
    public void testUpdateExistingKey() {
        client.write("config", "version1", false);
        assertEquals("version1", client.read("config").getValue());

        String updateResponse = client.write("config", "version2", false).getValue();
        assertEquals(entrySuccess, updateResponse);

        assertEquals("version2", client.read("config").getValue());
    }

    @Test
    public void testWriteAndReadWithDirectory() {
        ZookeeperClient.QueryResult response1 = client.write("employee1", "Engineering", "department", false);
        ZookeeperClient.QueryResult response2 = client.write("employee1", "$80000", "salary", false);
        ZookeeperClient.QueryResult response3 = client.write("employee1", "Senior Developer", "position", false);

        assertEquals(entrySuccess, response1.getValue());
        assertEquals(entrySuccess, response2.getValue());
        assertEquals(entrySuccess, response3.getValue());

        assertEquals("Engineering", client.read("employee1", "department").getValue());
        assertEquals("$80000", client.read("employee1", "salary").getValue());
        assertEquals("Senior Developer", client.read("employee1", "position").getValue());
    }

    @Test
    public void testMultipleEntriesWithDirectories() {
        client.write("product1", "Laptop", "name", false);
        client.write("product1", "$1200", "price", false);
        client.write("product2", "Mouse", "name", false);
        client.write("product2", "$25", "price", false);

        assertEquals("Laptop", client.read("product1", "name").getValue());
        assertEquals("$1200", client.read("product1", "price").getValue());
        assertEquals("Mouse", client.read("product2", "name").getValue());
        assertEquals("$25", client.read("product2", "price").getValue());
    }

    @Test
    public void testDeleteBasicEntry() {
        client.write("temp", "temporary data", false);
        assertEquals("temporary data", client.read("temp").getValue());

        boolean deleteResult = client.delete("temp").isSuccess();

        String readAfterDelete = client.read("temp").getValue();
        assertEquals(notFound, readAfterDelete, "Deleted entry should not be found");
    }

    @Test
    public void testDeleteEntryWithDirectory() {
        client.write("session1", "active", "status", false);
        assertEquals("active", client.read("session1", "status").getValue());

        boolean deleteResult = client.delete("session1", "status").isSuccess();

        String readAfterDelete = client.read("session1", "status").getValue();
        assertEquals(notFound, readAfterDelete, "Deleted entry should not be found");
    }

    @Test
    public void testReadNonExistentKey() {
        String result = client.read("nonexistent_key").getValue();
        assertEquals(notFound, result, "Non-existent key should return __NOT_FOUND__");
    }

    @Test
    public void testReadNonExistentDirectory() {
        String result = client.read("some_key", "nonexistent_directory").getValue();
        assertEquals(notFound, result, "Non-existent directory should return __NOT_FOUND__");
    }

    @Test
    public void testComplexScenario() {
        client.write("app_config", "production", "environment", false);
        client.write("app_config", "true", "debug_mode", false);
        client.write("app_config", "INFO", "log_level", false);

        assertEquals("production", client.read("app_config", "environment").getValue());
        assertEquals("true", client.read("app_config", "debug_mode").getValue());
        assertEquals("INFO", client.read("app_config", "log_level").getValue());

        client.write("app_config", "false", "debug_mode", false);
        assertEquals("false", client.read("app_config", "debug_mode").getValue());

        client.delete("app_config", "log_level");
        assertEquals(notFound, client.read("app_config", "log_level").getValue());

        assertEquals("production", client.read("app_config", "environment").getValue());
        assertEquals("false", client.read("app_config", "debug_mode").getValue());
    }

    @Test
    public void testSpecialCharactersInValues() {
        client.write("message", "Hello, World! @#$%^&*()", false);
        assertEquals("Hello, World! @#$%^&*()", client.read("message").getValue());

        client.write("json_like", "{\"key\":\"value\"}", false);
        assertEquals("{\"key\":\"value\"}", client.read("json_like").getValue());
    }

    @Test
    public void testLargeValueStorage() {
        StringBuilder largeValue = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            largeValue.append("This is line ").append(i).append(".");
        }

        String value = largeValue.toString();
        client.write("large_data", value, false);
        assertEquals(value, client.read("large_data").getValue());
    }

    @Test
    public void testSequentialOperations() {
        String key = "counter";

        client.write(key, "0", false);
        assertEquals("0", client.read(key).getValue());
        client.write(key, "1", false);
        assertEquals("1", client.read(key).getValue());
        client.write(key, "2", false);
        assertEquals("2", client.read(key).getValue());
        client.write(key, "3", false);
        assertEquals("3", client.read(key).getValue());
    }

    @Test
    public void testMixedBasicAndDirectoryOperations() {
        String key = "mixed_key";

        client.write(key, "base_value", false);
        assertEquals("base_value", client.read(key).getValue());

        client.write(key, "dir_value1", "dir1", false);
        client.write(key, "dir_value2", "dir2", false);

        assertEquals("base_value", client.read(key).getValue());
        assertEquals("dir_value1", client.read(key, "dir1").getValue());
        assertEquals("dir_value2", client.read(key, "dir2").getValue());
    }


    @Test
    public void testDeleteAndRewrite() {
        String key = "rewritable";

        client.write(key, "first_value", false);
        assertEquals("first_value", client.read(key).getValue());

        client.delete(key);
        assertEquals(notFound, client.read(key).getValue());

        client.write(key, "second_value", false);
        assertEquals("second_value", client.read(key).getValue());
    }

    @Test
    public void testMultipleDirectoriesPerKey() {
        String key = "user_profile";

        client.write(key, "Csed", "firstname", false);
        client.write(key, "Doe", "lastname", false);
        client.write(key, "csed@zookeeper.com", "email", false);
        client.write(key, "30", "age", false);
        client.write(key, "New York", "city", false);

        assertEquals("Csed", client.read(key, "firstname").getValue());
        assertEquals("Doe", client.read(key, "lastname").getValue());
        assertEquals("csed@zookeeper.com", client.read(key, "email").getValue());
        assertEquals("30", client.read(key, "age").getValue());
        assertEquals("New York", client.read(key, "city").getValue());
    }

    @Test
    public void testEphemeralEntryExpiration() throws Exception {
        //! Create a separate client for ephemeral session
        RaftClient ephemeralRaftClient = new RaftClientBuilder()
                .setPeers(IDS, PORTS)
                .setGroupId(GROUP_ID)
                .build();

        try (ZookeeperClient ephemeralClient = new ZookeeperClient(ephemeralRaftClient)) {
            ephemeralClient.register("ephemeral@user.com", "pass1234");
            ephemeralClient.login("ephemeral@user.com", "pass1234");
            String key = "ephemeralKey";
            String value = "temporary";

            ephemeralClient.write(key, value, true);
            assertEquals(value, ephemeralClient.read(key).getValue());

            ephemeralClient.logout();
            String readValue = client.read(key).getValue();
            assertEquals(notFound, readValue, "Ephemeral entry should be removed after session timeout");
        }
    }

}
