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

}
