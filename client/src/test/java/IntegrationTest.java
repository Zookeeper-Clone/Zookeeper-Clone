import static org.junit.jupiter.api.Assertions.*;

import org.apache.ratis.client.RaftClient;
import org.junit.jupiter.api.*;

import client.zookeeper.RaftClientBuilder;
import client.zookeeper.ZookeeperClient;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class IntegrationTest {

    private static RaftClient raftClient;
    private static ZookeeperClient client;
    private static final String entrySucess = "OK ENTRY ADDED";
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
        client.register("admin", "adminpass");
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
        String writeResponse = client.write("user1", "John Doe").getValue();
        assertEquals(entrySucess, writeResponse, "Write operation should succeed");

        String readResponse = client.read("user1").getValue();
        assertEquals("John Doe", readResponse, "Read should return the written value");
    }

    @Test
    @Order(2)
    public void testMultipleWrites() {
        String response1 = client.write("user2", "Alice").getValue();
        String response2 = client.write("user3", "Bob").getValue();
        String response3 = client.write("user4", "Charlie").getValue();

        assertEquals(entrySucess, response1);
        assertEquals(entrySucess, response2);
        assertEquals(entrySucess, response3);

        assertEquals("Alice", client.read("user2").getValue());
        assertEquals("Bob", client.read("user3").getValue());
        assertEquals("Charlie", client.read("user4").getValue());
    }

    @Test
    @Order(3)
    public void testUpdateExistingKey() {
        client.write("config", "version1");
        assertEquals("version1", client.read("config").getValue());

        String updateResponse = client.write("config", "version2").getValue();
        assertEquals(entrySucess, updateResponse);

        assertEquals("version2", client.read("config").getValue());
    }

    @Test
    @Order(4)
    public void testWriteAndReadWithDirectory() {
        ZookeeperClient.QueryResult response1 = client.write("employee1", "Engineering", "department");
        ZookeeperClient.QueryResult response2 = client.write("employee1", "$80000", "salary");
        ZookeeperClient.QueryResult response3 = client.write("employee1", "Senior Developer", "position");

        assertEquals(entrySucess, response1.getValue());
        assertEquals(entrySucess, response2.getValue());
        assertEquals(entrySucess, response3.getValue());

        assertEquals("Engineering", client.read("employee1", "department").getValue());
        assertEquals("$80000", client.read("employee1", "salary").getValue());
        assertEquals("Senior Developer", client.read("employee1", "position").getValue());
    }

    @Test
    @Order(5)
    public void testMultipleEntriesWithDirectories() {
        client.write("product1", "Laptop", "name");
        client.write("product1", "$1200", "price");
        client.write("product2", "Mouse", "name");
        client.write("product2", "$25", "price");

        assertEquals("Laptop", client.read("product1", "name").getValue());
        assertEquals("$1200", client.read("product1", "price").getValue());
        assertEquals("Mouse", client.read("product2", "name").getValue());
        assertEquals("$25", client.read("product2", "price").getValue());
    }

    @Test
    @Order(6)
    public void testDeleteBasicEntry() {
        client.write("temp", "temporary data");
        assertEquals("temporary data", client.read("temp").getValue());

        boolean deleteResult = client.delete("temp").isSuccess();

        String readAfterDelete = client.read("temp").getValue();
        assertEquals(notFound, readAfterDelete, "Deleted entry should not be found");
    }

    @Test
    @Order(7)
    public void testDeleteEntryWithDirectory() {
        client.write("session1", "active", "status");
        assertEquals("active", client.read("session1", "status").getValue());

        boolean deleteResult = client.delete("session1", "status").isSuccess();

        String readAfterDelete = client.read("session1", "status").getValue();
        assertEquals(notFound, readAfterDelete, "Deleted entry should not be found");
    }

    @Test
    @Order(8)
    public void testReadNonExistentKey() {
        String result = client.read("nonexistent_key").getValue();
        assertEquals(notFound, result, "Non-existent key should return __NOT_FOUND__");
    }

    @Test
    @Order(9)
    public void testReadNonExistentDirectory() {
        String result = client.read("some_key", "nonexistent_directory").getValue();
        assertEquals(notFound, result, "Non-existent directory should return __NOT_FOUND__");
    }

    @Test
    @Order(10)
    public void testComplexScenario() {
        client.write("app_config", "production", "environment");
        client.write("app_config", "true", "debug_mode");
        client.write("app_config", "INFO", "log_level");

        assertEquals("production", client.read("app_config", "environment").getValue());
        assertEquals("true", client.read("app_config", "debug_mode").getValue());
        assertEquals("INFO", client.read("app_config", "log_level").getValue());

        client.write("app_config", "false", "debug_mode");
        assertEquals("false", client.read("app_config", "debug_mode").getValue());

        client.delete("app_config", "log_level");
        assertEquals(notFound, client.read("app_config", "log_level").getValue());

        assertEquals("production", client.read("app_config", "environment").getValue());
        assertEquals("false", client.read("app_config", "debug_mode").getValue());
    }

    @Test
    @Order(11)
    public void testSpecialCharactersInValues() {
        client.write("message", "Hello, World! @#$%^&*()");
        assertEquals("Hello, World! @#$%^&*()", client.read("message").getValue());

        client.write("json_like", "{\"key\":\"value\"}");
        assertEquals("{\"key\":\"value\"}", client.read("json_like").getValue());
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
        assertEquals(value, client.read("large_data").getValue());
    }

    @Test
    @Order(13)
    public void testSequentialOperations() {
        String key = "counter";

        client.write(key, "0");
        assertEquals("0", client.read(key).getValue());
        client.write(key, "1");
        assertEquals("1", client.read(key).getValue());
        client.write(key, "2");
        assertEquals("2", client.read(key).getValue());
        client.write(key, "3");
        assertEquals("3", client.read(key).getValue());
    }

    @Test
    @Order(14)
    public void testMixedBasicAndDirectoryOperations() {
        String key = "mixed_key";

        client.write(key, "base_value");
        assertEquals("base_value", client.read(key).getValue());

        client.write(key, "dir_value1", "dir1");
        client.write(key, "dir_value2", "dir2");

        assertEquals("base_value", client.read(key).getValue());
        assertEquals("dir_value1", client.read(key, "dir1").getValue());
        assertEquals("dir_value2", client.read(key, "dir2").getValue());
    }


    @Test
    @Order(15)
    public void testDeleteAndRewrite() {
        String key = "rewritable";

        client.write(key, "first_value");
        assertEquals("first_value", client.read(key).getValue());

        client.delete(key);
        assertEquals(notFound, client.read(key).getValue());

        client.write(key, "second_value");
        assertEquals("second_value", client.read(key).getValue());
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

        assertEquals("Csed", client.read(key, "firstname").getValue());
        assertEquals("Doe", client.read(key, "lastname").getValue());
        assertEquals("csed@zookeeper.com", client.read(key, "email").getValue());
        assertEquals("30", client.read(key, "age").getValue());
        assertEquals("New York", client.read(key, "city").getValue());
    }

}
