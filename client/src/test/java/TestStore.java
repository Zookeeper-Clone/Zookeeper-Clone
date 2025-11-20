import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import client.zookeeper.ZookeeperClient;

public class TestStore {

    private MockRaftClient mockRaftClient;
    private ZookeeperClient zookeeperClient;

    @BeforeEach
    public void setUp() {
        mockRaftClient = new MockRaftClient();
        zookeeperClient = new ZookeeperClient(mockRaftClient);
    }

    @Test
    public void addEntry() {
        String key = "key";
        String value = "value";
        String response = zookeeperClient.write(key, value);
        assertEquals("OK ENTRY ADDED", response);
    }

    @Test
    public void readEntry() {
        String key = "key";
        String value = "value";
        zookeeperClient.write(key, value);
        String response = zookeeperClient.read(key);
        assertEquals("value", response);
    }

    @Test
    public void deleteEntry() {
        String key = "key";
        String value = "value";
        zookeeperClient.write(key, value);
        boolean deleteResponse = zookeeperClient.delete(key);
        assert(deleteResponse);
        String response = zookeeperClient.read(key);
        assertEquals("KEY DOESN'T EXIST", response);
    }

    @Test
    public void multiWriteEntry() {
        String key1 = "key1";
        String value1 = "value1";
        String writeResponse1 = zookeeperClient.write(key1, value1);

        String key2 = "key2";
        String value2 = "value2";
        String writeResponse2 = zookeeperClient.write(key2, value2);

        String readResponse1 = zookeeperClient.read(key1);
        String readResponse2 = zookeeperClient.read(key2);

        assertEquals("OK ENTRY ADDED", writeResponse1);
        assertEquals("OK ENTRY ADDED", writeResponse2);
        assertEquals("value1", readResponse1);
        assertEquals("value2", readResponse2);
    }

    @Test
    public void updateEntry() {
        String key1 = "key1";
        String value1 = "value1";
        zookeeperClient.write(key1, value1);

        value1 = "newValue1";
        String response = zookeeperClient.write(key1, value1);
        assertEquals("OK ENTRY UPDATED", response);

        String readResponse = zookeeperClient.read(key1);
        assertEquals(value1, readResponse);
    }

    @Test
    public void displayAllEntry() {
        String key1 = "key1";
        String value1 = "value1";
        zookeeperClient.write(key1, value1);

        String key2 = "key2";
        String value2 = "value2";
        zookeeperClient.write(key2, value2);

        String readAll = zookeeperClient.readAll();
        assertEquals("key1 : value1\nkey2 : value2\n", readAll);
    }
}
