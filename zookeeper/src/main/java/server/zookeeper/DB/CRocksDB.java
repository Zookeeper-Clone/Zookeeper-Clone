package server.zookeeper.DB;

import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("unused")
public class CRocksDB implements DataBase, Closeable {

    // private static final Logger LOG = LoggerFactory.getLogger(CRocksDB.class);
    private static volatile CRocksDB instance = null;
    private static RocksDB db;

    private final List<ColumnFamilyHandle> cfHandles = new ArrayList<>();

    private ColumnFamilyHandle defaultCf;
    private ColumnFamilyHandle authCf;

    private DBOptions options;

    @SuppressWarnings("resource")
    private CRocksDB(){
        RocksDB.loadLibrary();
        try {
            options = new DBOptions()
                .setCreateIfMissing(true)
                .setCreateMissingColumnFamilies(true); 
            final List<ColumnFamilyDescriptor> cfDescriptors = Arrays.asList(
                new ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, new ColumnFamilyOptions()),
                new ColumnFamilyDescriptor("Authentication".getBytes(), new ColumnFamilyOptions())
            );
            db = RocksDB.open(options,"/RocksDB",cfDescriptors,cfHandles);

            defaultCf = cfHandles.get(0);
            authCf = cfHandles.get(1);
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
            return db.get(defaultCf,key);
        } catch (RocksDBException e) {
            String keyString = new String(key, StandardCharsets.UTF_8);
            throw new RuntimeException(String.format("Error while retrieving value for key %s", keyString), e);
        }
    }

    @Override
    public void put(byte[] key, byte[] val) {
        try {
            db.put(defaultCf,key, val);
        } catch (RocksDBException e) {
            String keyString = new String(key, StandardCharsets.UTF_8);
            throw new RuntimeException(String.format("Error while inserting key %s", keyString), e);
        }
    }

    @Override
    public void delete(byte[] key) {
        try {
            db.delete(defaultCf,key);
        } catch (RocksDBException e) {
            String keyString = new String(key, StandardCharsets.UTF_8);
            throw new RuntimeException(String.format("Error while deleting key %s", keyString), e);
        }
    }

    @Override
    public void close() throws IOException {
        for (ColumnFamilyHandle h : cfHandles){
            h.close();
        }
        db.close();
        options.close();
    }
}
