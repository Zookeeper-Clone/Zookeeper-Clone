package server.zookeeper.DB;

import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class CRocksDB implements DataBase, Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(CRocksDB.class);
    private static volatile CRocksDB instance = null;
    private static RocksDB db;

    private CRocksDB(){
        RocksDB.loadLibrary();
        try (final Options options = new Options().setCreateIfMissing(true)) {
            db = RocksDB.open(options, "/RocksDB"); // TODO : make it an env variable
        } catch (RocksDBException e) {
            throw new RuntimeException("Error opening the DataBase", e);
        }
    }

    public static DataBase getInstance() {
        if(null == instance) {
            synchronized (CRocksDB.class){
                if(null == instance) {
                    return instance = new CRocksDB();
                }
            }
        }
        return instance;
    }

    @Override
    public byte[] get(byte[] key) {
        try {
            return db.get(key);
        } catch (RocksDBException e) {
            String keyString = new String(key, StandardCharsets.UTF_8);
            throw new RuntimeException(String.format("Error while retrieving value for key %s", keyString), e);
        }
    }

    @Override
    public void put(byte[] key, byte[] val) {
        try {
            db.put(key, val);
        } catch (RocksDBException e) {
            String keyString = new String(key, StandardCharsets.UTF_8);
            throw new RuntimeException(String.format("Error while inserting key %s", keyString), e);
        }
    }

    @Override
    public void delete(byte[] key) {
        try {
            db.delete(key);
        } catch (RocksDBException e) {
            String keyString = new String(key, StandardCharsets.UTF_8);
            throw new RuntimeException(String.format("Error while deleting key %s", keyString), e);
        }
    }

    @Override
    public void close() throws IOException {
        db.close();
    }
}
