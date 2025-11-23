package server.zookeeper.DB;

public interface DataBase {
    byte[] get(byte[] key);
    void put(byte[] key, byte[] val);
    void delete(byte[] key);
}
