import static org.junit.jupiter.api.Assertions.*;

import org.apache.ratis.client.RaftClient;
import org.junit.jupiter.api.*;

import client.zookeeper.RaftClientBuilder;
import client.zookeeper.ZookeeperClient;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class IntegrationTest {

    private static RaftClient raftClient;
    private static ZookeeperClient client;
    
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
    }

    @AfterAll
    public static void tearDown() throws Exception {
        if (raftClient != null) {
            raftClient.close();
        }
    }

    @Test
    @Order(1)
    public void testBasicWriteAndRead() {
        String writeResponse = client.write("user1", "John Doe");
        assertEquals("OK ENTRY ADDED", writeResponse, "Write operation should succeed");

        String readResponse = client.read("user1");
        assertEquals("John Doe", readResponse, "Read should return the written value");
    }

    @Test
    @Order(2)
    public void testMultipleWrites() {
        String response1 = client.write("user2", "Alice");
        String response2 = client.write("user3", "Bob");
        String response3 = client.write("user4", "Charlie");

        assertEquals("OK ENTRY ADDED", response1);
        assertEquals("OK ENTRY ADDED", response2);
        assertEquals("OK ENTRY ADDED", response3);

        assertEquals("Alice", client.read("user2"));
        assertEquals("Bob", client.read("user3"));
        assertEquals("Charlie", client.read("user4"));
    }

    @Test
    @Order(3)
    public void testUpdateExistingKey() {
        client.write("config", "version1");
        assertEquals("version1", client.read("config"));

        String updateResponse = client.write("config", "version2");
        assertEquals("OK ENTRY ADDED", updateResponse);

        assertEquals("version2", client.read("config"));
    }

    @Test
    @Order(4)
    public void testWriteAndReadWithDirectory() {
        String response1 = client.write("employee1", "Engineering", "department");
        String response2 = client.write("employee1", "$80000", "salary");
        String response3 = client.write("employee1", "Senior Developer", "position");

        assertEquals("OK ENTRY ADDED", response1);
        assertEquals("OK ENTRY ADDED", response2);
        assertEquals("OK ENTRY ADDED", response3);

        assertEquals("Engineering", client.read("employee1", "department"));
        assertEquals("$80000", client.read("employee1", "salary"));
        assertEquals("Senior Developer", client.read("employee1", "position"));
    }

    @Test
    @Order(5)
    public void testMultipleEntriesWithDirectories() {
        client.write("product1", "Laptop", "name");
        client.write("product1", "$1200", "price");
        client.write("product2", "Mouse", "name");
        client.write("product2", "$25", "price");

        assertEquals("Laptop", client.read("product1", "name"));
        assertEquals("$1200", client.read("product1", "price"));
        assertEquals("Mouse", client.read("product2", "name"));
        assertEquals("$25", client.read("product2", "price"));
    }

    @Test
    @Order(6)
    public void testDeleteBasicEntry() {
        client.write("temp", "temporary data");
        assertEquals("temporary data", client.read("temp"));

        boolean deleteResult = client.delete("temp");

        String readAfterDelete = client.read("temp");
        assertEquals("__NOT_FOUND__", readAfterDelete, "Deleted entry should not be found");
    }

    @Test
    @Order(7)
    public void testDeleteEntryWithDirectory() {
        client.write("session1", "active", "status");
        assertEquals("active", client.read("session1", "status"));

        boolean deleteResult = client.delete("session1", "status");

        String readAfterDelete = client.read("session1", "status");
        assertEquals("__NOT_FOUND__", readAfterDelete, "Deleted entry should not be found");
    }

    @Test
    @Order(8)
    public void testReadNonExistentKey() {
        String result = client.read("nonexistent_key");
        assertEquals("__NOT_FOUND__", result, "Non-existent key should return __NOT_FOUND__");
    }

    @Test
    @Order(9)
    public void testReadNonExistentDirectory() {
        String result = client.read("some_key", "nonexistent_directory");
        assertEquals("__NOT_FOUND__", result, "Non-existent directory should return __NOT_FOUND__");
    }

    @Test
    @Order(10)
    public void testComplexScenario() {
        client.write("app_config", "production", "environment");
        client.write("app_config", "true", "debug_mode");
        client.write("app_config", "INFO", "log_level");

        assertEquals("production", client.read("app_config", "environment"));
        assertEquals("true", client.read("app_config", "debug_mode"));
        assertEquals("INFO", client.read("app_config", "log_level"));

        client.write("app_config", "false", "debug_mode");
        assertEquals("false", client.read("app_config", "debug_mode"));

        client.delete("app_config", "log_level");
        assertEquals("__NOT_FOUND__", client.read("app_config", "log_level"));

        assertEquals("production", client.read("app_config", "environment"));
        assertEquals("false", client.read("app_config", "debug_mode"));
    }

    @Test
    @Order(11)
    public void testSpecialCharactersInValues() {
        client.write("message", "Hello, World! @#$%^&*()");
        assertEquals("Hello, World! @#$%^&*()", client.read("message"));

        client.write("json_like", "{\"key\":\"value\"}");
        assertEquals("{\"key\":\"value\"}", client.read("json_like"));
    }

    @Test
    @Order(12)
    public void testLargeValueStorage() {
        StringBuilder largeValue = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            largeValue.append("This is line ").append(i).append(".");
        }

        String value = largeValue.toString();
        client.write("large_data", value);
        assertEquals(value, client.read("large_data"));
    }

    @Test
    @Order(13)
    public void testSequentialOperations() {
        String key = "counter";

        client.write(key, "0");
        assertEquals("0", client.read(key));

        client.write(key, "1");
        assertEquals("1", client.read(key));

        client.write(key, "2");
        assertEquals("2", client.read(key));

        client.write(key, "3");
        assertEquals("3", client.read(key));
    }

    @Test
    @Order(14)
    public void testMixedBasicAndDirectoryOperations() {
        String key = "mixed_key";

        client.write(key, "base_value");
        assertEquals("base_value", client.read(key));

        client.write(key, "dir_value1", "dir1");
        client.write(key, "dir_value2", "dir2");

        assertEquals("base_value", client.read(key));
        assertEquals("dir_value1", client.read(key, "dir1"));
        assertEquals("dir_value2", client.read(key, "dir2"));
    }


    @Test
    @Order(15)
    public void testDeleteAndRewrite() {
        String key = "rewritable";

        client.write(key, "first_value");
        assertEquals("first_value", client.read(key));

        client.delete(key);
        assertEquals("__NOT_FOUND__", client.read(key));

        client.write(key, "second_value");
        assertEquals("second_value", client.read(key));
    }

    @Test
    @Order(16)
    public void testMultipleDirectoriesPerKey() {
        String key = "user_profile";

        client.write(key, "Csed", "firstname");
        client.write(key, "Doe", "lastname");
        client.write(key, "csed@zookeeper.com", "email");
        client.write(key, "30", "age");
        client.write(key, "New York", "city");

        assertEquals("Csed", client.read(key, "firstname"));
        assertEquals("Doe", client.read(key, "lastname"));
        assertEquals("csed@zookeeper.com", client.read(key, "email"));
        assertEquals("30", client.read(key, "age"));
        assertEquals("New York", client.read(key, "city"));
    }

    @Test
    @Order(17)
    public void testReadAll() {
        String allEntries = client.readAll();
        assertNotNull(allEntries, "ReadAll should return a non-null result");
        assertFalse(allEntries.equals("ERROR"), "ReadAll should not return ERROR");
    }
}
