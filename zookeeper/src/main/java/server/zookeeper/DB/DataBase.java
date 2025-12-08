package server.zookeeper.DB;

import java.nio.file.Path;

public interface DataBase {
    byte[] get(byte[] key);
    void put(byte[] key, byte[] val);
    void delete(byte[] key);

    byte[] get(byte[] key, String directory);
    void put(byte[] key, byte[] val, String directory);
    void delete(byte[] key, String directory);

    void takeSnapshot(String path);
    void loadSnapshot(String path);
}
