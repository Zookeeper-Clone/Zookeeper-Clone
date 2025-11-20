import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import client.zookeeper.ZookeeperClient;

public class TestStore {
    @Test
    public void addEntry(){
        MockRaftClient mockRaftClient = new MockRaftClient();
        ZookeeperClient zookeeperClient = new ZookeeperClient(mockRaftClient);
        String key = "key";
        String value = "value";
        String response = zookeeperClient.write(key, value);
        assertEquals("OK ENTRY ADDED", response);
    }
    @Test
    public void readEntry(){
        MockRaftClient mockRaftClient = new MockRaftClient();
        ZookeeperClient zookeeperClient = new ZookeeperClient(mockRaftClient);
        String key = "key";
        String value = "value";
        zookeeperClient.write(key, value);
        String response = zookeeperClient.read(key);
        assertEquals(response, "value");
    }
    @Test
    public void deleteEntry(){
        MockRaftClient mockRaftClient = new MockRaftClient();
        ZookeeperClient zookeeperClient = new ZookeeperClient(mockRaftClient);
        String key = "key";
        String value = "value";
        zookeeperClient.write(key, value);
        boolean deleteResponse = zookeeperClient.delete(key);
        assert(deleteResponse);
        String response = zookeeperClient.read(key);
        assertEquals(response, "KEY DOESN'T EXIST");
    }
    @Test
    public void multiWriteEntry(){
        MockRaftClient mockRaftClient = new MockRaftClient();
        ZookeeperClient zookeeperClient = new ZookeeperClient(mockRaftClient);
        String key1 = "key1";
        String value1 = "value1";
        String writeResponse1 = zookeeperClient.write(key1, value1);
        String key2 = "key2";
        String value2 = "value2";
        String writeResponse2 = zookeeperClient.write(key2, value2);
        String readResponse1 = zookeeperClient.read(key1);
        String readResponse2 = zookeeperClient.read(key2);
        assertEquals(writeResponse1, "OK ENTRY ADDED");
        assertEquals(writeResponse2, "OK ENTRY ADDED");
        assertEquals(readResponse1, "value1");
        assertEquals(readResponse2, "value2");
    }
    @Test
    public void updateEntry(){
        MockRaftClient mockRaftClient = new MockRaftClient();
        ZookeeperClient zookeeperClient = new ZookeeperClient(mockRaftClient);
        String key1 = "key1";
        String value1 = "value1";
        zookeeperClient.write(key1, value1);
        value1 = "newValue1";
        String response = zookeeperClient.write(key1, value1);
        assertEquals(response, "OK ENTRY UPDATED");
        String readResponse = zookeeperClient.read(key1);
        assertEquals(readResponse, value1);
    }
    @Test
    public void displayAllEntry()
    {
        MockRaftClient mockRaftClient = new MockRaftClient();
        ZookeeperClient zookeeperClient = new ZookeeperClient(mockRaftClient);
        String key1 = "key1";
        String value1 = "value1";
        zookeeperClient.write(key1, value1);
        String key2 = "key2";
        String value2 = "value2";
        zookeeperClient.write(key2, value2);
        String readAll = zookeeperClient.readAll();
        assertEquals(readAll, "key1 : value1\nkey2 : value2\n");
    }
}
