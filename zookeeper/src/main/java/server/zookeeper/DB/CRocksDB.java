package server.zookeeper.DB;

import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.Checkpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import server.zookeeper.util.EnvUtils;
import server.zookeeper.util.ReservedDirectories;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


@SuppressWarnings("unused")
public class CRocksDB implements DataBase, Closeable {

     private static final Logger LOG = LoggerFactory.getLogger(CRocksDB.class);
    private static volatile CRocksDB instance = null;
    private static RocksDB db;
    private final HashMap<String, ColumnFamilyHandle> cfHandles = new HashMap<>();
    private final Options options;
    private final String DBPath;

    private CRocksDB() {
        RocksDB.loadLibrary();
        try {
            DBPath = EnvUtils.getRequiredEnv("DB_PATH");
            RocksDB.destroyDB(DBPath, new Options());
            options = new Options()
                    .setCreateIfMissing(true);
            db = RocksDB.open(options, DBPath);
            initializeReservedDirectories();
        } catch (RocksDBException e) {
            throw new RuntimeException("Error opening the DataBase", e);
        }
    }

    private void initializeReservedDirectories() {
        LOG.info("Initializing system-reserved directories...");

        for (String reservedDir : ReservedDirectories.getReservedDirectories()) {
            try {
                createColumnFamily(reservedDir);
                LOG.info("Initialized reserved directory: {}", reservedDir);
            } catch (Exception e) {
                LOG.error("Failed to initialize reserved directory: {}", reservedDir, e);
                throw new RuntimeException("Failed to initialize system directories", e);
            }
        }

        LOG.info("System-reserved directories initialized successfully");
    }

    public static DataBase getInstance() {
        if (null != instance) return instance;

        synchronized (CRocksDB.class) {
            if (null == instance) {
                return instance = new CRocksDB();
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

        if (cfHandles.containsKey(columnName)) {
            return cfHandles.get(columnName);
        } else {
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
            throw new IllegalArgumentException(String.format("Error while retrieving value for key %s", keyString), e);
        }
    }

    @Override
    public byte[] get(byte[] key, String cFamilyName) {
        try {
            ColumnFamilyHandle cFamilyHandle = getColumnFamilyHandle(cFamilyName);
            return db.get(cFamilyHandle,key);
        } catch (Exception e) {
            String keyString = new String(key, StandardCharsets.UTF_8);
            throw new IllegalArgumentException(String.format("Error while retrieving value for key %s", keyString), e);
        }
    }

    @Override
    public void put(byte[] key, byte[] val) {
        try {
            db.put(key, val);
        } catch (RocksDBException e) {
            String keyString = new String(key, StandardCharsets.UTF_8);
            throw new IllegalArgumentException(String.format("Error while inserting key %s", keyString), e);
        }
    }

    @Override
    public void put(byte[] key, byte[] val, String cFamilyName) {
        try {
            ColumnFamilyHandle cFamilyHandle = getColumnFamilyHandle(cFamilyName);
            db.put(cFamilyHandle,key,val);
        } catch (RocksDBException e) {
            String keyString = new String(key, StandardCharsets.UTF_8);
            throw new IllegalArgumentException(String.format("Error while inserting key %s", keyString), e);
        }
    }

    @Override
    public void delete(byte[] key) {
        try {
            db.delete(key);
        } catch (RocksDBException e) {
            String keyString = new String(key, StandardCharsets.UTF_8);
            throw new IllegalArgumentException(String.format("Error while deleting key %s", keyString), e);
        }
    }

    @Override
    public void delete(byte[] key, String cFamilyName) {
        try {
            ColumnFamilyHandle cFamilyHandle = getColumnFamilyHandle(cFamilyName);
            db.delete(cFamilyHandle,key);
        } catch (RocksDBException e) {
            String keyString = new String(key, StandardCharsets.UTF_8);
            throw new IllegalArgumentException(String.format("Error while deleting key %s", keyString), e);
        }
    }

    @Override
    public void takeSnapshot(String path) {
        try (Checkpoint cp = Checkpoint.create(db)) {
            cp.createCheckpoint(path);
        } catch (RocksDBException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized void loadSnapshot(String snapshotAbsPath) {
        try {
            for (ColumnFamilyHandle h : cfHandles.values()) {
                h.close();
            }
            cfHandles.clear();
            db.close();

            Path dbPath = Path.of(DBPath);
            cleanUpDBDirectory(dbPath);

            Path snapshotPath = Path.of(snapshotAbsPath);
            if (!Files.isDirectory(snapshotPath)) {
                throw new IllegalArgumentException("Snapshot path is not a directory: " + snapshotAbsPath);
            }

            Files.createDirectories(dbPath);

            copySnapshotContents(snapshotPath, dbPath);

            db = RocksDB.open(options, DBPath);

            // Reload column families
            var cfNames = RocksDB.listColumnFamilies(new Options(), DBPath);
            for (byte[] nameBytes : cfNames) {
                String cfName = new String(nameBytes, StandardCharsets.UTF_8);
                ColumnFamilyHandle handle = db.createColumnFamily(
                        new ColumnFamilyDescriptor(nameBytes, new ColumnFamilyOptions()));
                cfHandles.put(cfName, handle);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to load snapshot", e);
        }
    }

    private static void copySnapshotContents(Path snapshotPath, Path dbPath) throws IOException {
        try (var walk = Files.walk(snapshotPath)) {
            walk.forEach(src -> {
                try {
                    Path dest = dbPath.resolve(snapshotPath.relativize(src));
                    if (Files.isDirectory(src)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.copy(src, dest,
                                StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Failed to copy snapshot file", e);
                }
            });
        }
    }

    private static void cleanUpDBDirectory(Path dbPath) throws IOException {
        if (Files.exists(dbPath)) {
            try (var walk = Files.walk(dbPath)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (Exception e) {
                                throw new RuntimeException("Failed to delete DB directory contents", e);
                            }
                        });
            }
        }
    }

    @Override
    public void close() {
        Iterator<Map.Entry<String, ColumnFamilyHandle>> it = cfHandles.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ColumnFamilyHandle> entry = it.next();
            ColumnFamilyHandle handle = entry.getValue();
            if (handle != null) {
                handle.close();
            }
            it.remove();
        }

        if (db != null) {
            db.close();
            db = null;
        }
        if (options != null) {
            options.close();
        }
    }
}
