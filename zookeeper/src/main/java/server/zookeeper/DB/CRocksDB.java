package server.zookeeper.DB;

import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.management.RuntimeErrorException;

@SuppressWarnings("unused")
public class CRocksDB implements DataBase, Closeable {

    // private static final Logger LOG = LoggerFactory.getLogger(CRocksDB.class);
    private static volatile CRocksDB instance = null;
    private static RocksDB db;
    private HashMap<String, ColumnFamilyHandle> cfHandles = new HashMap<>();
    private Options options;

    @SuppressWarnings("resource")
    private CRocksDB() {
        RocksDB.loadLibrary();
        try {
            RocksDB.destroyDB("/RocksDB", new Options());
            options = new Options()
                    .setCreateIfMissing(true);
            db = RocksDB.open(options, "/RocksDB");
        } catch (RocksDBException e) {
            throw new RuntimeException("Error opening the DataBase", e);
        }
    }

    public static DataBase getInstance() {
        if (null == instance) {
            synchronized (CRocksDB.class) {
                if (null == instance) {
                    return instance = new CRocksDB();
                }
            }
        }
        return instance;
    }

    private ColumnFamilyHandle createColumnFamily(String columnName) {
        try {
            ColumnFamilyDescriptor newCFamilyDescriptor = new ColumnFamilyDescriptor(
                    columnName.getBytes(StandardCharsets.UTF_8),
                    new ColumnFamilyOptions());
            ColumnFamilyHandle handle = db.createColumnFamily(newCFamilyDescriptor);
            cfHandles.put(columnName, handle);
            return handle;
        } catch (Exception e) {
            throw new RuntimeException("Error creating Column Family", e);
        }
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
    public byte[] get(byte[] key, String cFamilyName) {
        try {
            if (cfHandles.containsKey(cFamilyName)) {
                ColumnFamilyHandle handle = cfHandles.get(cFamilyName);
                return db.get(handle, key);
            } else {
                ColumnFamilyHandle handle = createColumnFamily(cFamilyName);
                return db.get(handle, key);
            }

        } catch (Exception e) {
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
    public void put(byte[] key, byte[] val, String cFamilyName) {
        try {
            if (cfHandles.containsKey(cFamilyName)) {
                ColumnFamilyHandle handle = cfHandles.get(cFamilyName);
                db.put(handle, key, val);
            } else {
                ColumnFamilyHandle handle = createColumnFamily(cFamilyName);
                db.put(handle, key, val);
            }
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
    public void delete(byte[] key, String cFamilyName) {
        try {
            if (cfHandles.containsKey(cFamilyName)) {
                ColumnFamilyHandle handle = cfHandles.get(cFamilyName);
                db.delete(handle, key);
            } else {
                ColumnFamilyHandle handle = createColumnFamily(cFamilyName);
                db.delete(handle, key);
            }
        } catch (RocksDBException e) {
            String keyString = new String(key, StandardCharsets.UTF_8);
            throw new RuntimeException(String.format("Error while deleting key %s", keyString), e);
        }
    }

    @Override
    public void close() throws IOException {
        for (ColumnFamilyHandle cFamilyHandle : cfHandles.values()) {
            cFamilyHandle.close();
        }
        db.close();
        options.close();
    }
}
