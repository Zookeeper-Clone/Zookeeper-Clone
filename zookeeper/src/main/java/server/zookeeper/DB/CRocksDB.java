package server.zookeeper.DB;

import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import server.zookeeper.util.EnvUtils;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

@SuppressWarnings("unused")
public class CRocksDB implements DataBase, Closeable {

    // private static final Logger LOG = LoggerFactory.getLogger(CRocksDB.class);
    private static volatile CRocksDB instance = null;
    private static RocksDB db;
    private HashMap<String, ColumnFamilyHandle> cfHandles = new HashMap<>();
    private Options options;
    private String DBPath;
    @SuppressWarnings("resource")
    private CRocksDB() {
        RocksDB.loadLibrary();
        try {
            DBPath = EnvUtils.getRequiredEnv("DB_PATH"); 
            RocksDB.destroyDB(DBPath, new Options());
            options = new Options()
                    .setCreateIfMissing(true);
            db = RocksDB.open(options, DBPath);
        } catch (RocksDBException e) {
            throw new RuntimeException("Error opening the DataBase", e);
        }
    }

    public static CRocksDB getInstance() {
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
    private ColumnFamilyHandle getColumnFamilyHandle(String columnName){
        if(cfHandles.containsKey(columnName)){
            return cfHandles.get(columnName);
        }else{
            ColumnFamilyHandle newHandle = createColumnFamily(columnName);
            cfHandles.put(columnName, newHandle);
            return newHandle;
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
            ColumnFamilyHandle cFamilyHandle = getColumnFamilyHandle(cFamilyName);
            return db.get(cFamilyHandle,key);
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
            ColumnFamilyHandle cFamilyHandle = getColumnFamilyHandle(cFamilyName);
            db.put(cFamilyHandle,key,val);
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
            ColumnFamilyHandle cFamilyHandle = getColumnFamilyHandle(cFamilyName);
            db.delete(cFamilyHandle,key);
        } catch (RocksDBException e) {
            String keyString = new String(key, StandardCharsets.UTF_8);
            throw new RuntimeException(String.format("Error while deleting key %s", keyString), e);
        }
    }

    @Override
    public void close() throws IOException {
        for (String cFname : cfHandles.keySet()) {
            ColumnFamilyHandle cFamilyHandle = cfHandles.get(cFname);
            cfHandles.remove(cFname);
            cFamilyHandle.close();
        }
        db.close();
        options.close();
    }
}
