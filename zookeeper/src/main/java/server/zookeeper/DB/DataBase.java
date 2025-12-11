package server.zookeeper.DB;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface DataBase {
    byte[] get(byte[] key);
    void put(byte[] key, byte[] val);
    void delete(byte[] key);

    byte[] get(byte[] key, String directory);
    void put(byte[] key, byte[] val, String directory);
    void delete(byte[] key, String directory);

    /**
     * Get all key-value pairs in a column family/directory.
     * @param directory The column family name
     * @return List of key-value pairs
     */
    List<Map.Entry<byte[], byte[]>> getAllEntries(String directory);

    void takeSnapshot(String path);
    void loadSnapshot(String path);
}
