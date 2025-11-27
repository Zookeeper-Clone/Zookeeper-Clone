package server.zookeeper.DB;

public interface DataBase {
    byte[] get(byte[] key);
    void put(byte[] key, byte[] val);
    void delete(byte[] key);

    byte[] get(byte[] key, String directory);
    void put(byte[] key, byte[] val, String directory);
    void delete(byte[] key, String directory);
}
