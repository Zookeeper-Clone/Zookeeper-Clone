package server.zookeeper.DB;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import server.zookeeper.util.EnvUtils;
import server.zookeeper.util.ReservedDirectories;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CRocksDBTest {

    @TempDir
    Path tempDir;

    private MockedStatic<EnvUtils> envUtilsMock;
    private MockedStatic<ReservedDirectories> reservedDirectoriesMock;

    @BeforeEach
    void setUp() throws Exception {
        resetSingleton();

        envUtilsMock = mockStatic(EnvUtils.class);
        String dbPath = tempDir.resolve("rocksdb_test").toAbsolutePath().toString();
        envUtilsMock.when(() -> EnvUtils.getRequiredEnv("DB_PATH")).thenReturn(dbPath);

        reservedDirectoriesMock = mockStatic(ReservedDirectories.class);
        reservedDirectoriesMock.when(ReservedDirectories::getReservedDirectories)
                .thenReturn(Set.of("__ZK_SYS_AUTH__"));
        reservedDirectoriesMock.when(() -> ReservedDirectories.isReserved("__ZK_SYS_AUTH__"))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            DataBase db = CRocksDB.getInstance();
            if (db instanceof CRocksDB) {
                ((CRocksDB) db).close();
            }
        } finally {
            if (envUtilsMock != null) {
                envUtilsMock.close();
            }
            if (reservedDirectoriesMock != null) {
                reservedDirectoriesMock.close();
            }
            resetSingleton();
        }
    }

    private void resetSingleton() throws Exception {
        Field instance = CRocksDB.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    @Test
    void testSingletonInstance() {
        DataBase db1 = CRocksDB.getInstance();
        DataBase db2 = CRocksDB.getInstance();
        assertNotNull(db1);
        assertSame(db1, db2);
    }

    @Test
    void testDefaultColumnFamilyCRUD() {
        DataBase db = CRocksDB.getInstance();
        byte[] key = "key1".getBytes(StandardCharsets.UTF_8);
        byte[] value = "value1".getBytes(StandardCharsets.UTF_8);

        db.put(key, value);
        assertArrayEquals(value, db.get(key));

        db.delete(key);
        assertNull(db.get(key));
    }

    @Test
    void testDynamicColumnFamilyCreation() {
        DataBase db = CRocksDB.getInstance();
        String newCF = "dynamic_cf";
        byte[] key = "data".getBytes(StandardCharsets.UTF_8);
        byte[] value = "content".getBytes(StandardCharsets.UTF_8);

        db.put(key, value, newCF);
        assertArrayEquals(value, db.get(key, newCF));
    }

    @Test
    void testExceptionHandlingOnClosedDB() throws Exception {
        CRocksDB db = (CRocksDB) CRocksDB.getInstance();
        db.close();

        byte[] data = "test".getBytes();

        assertThrows(RuntimeException.class, () -> db.put(data, data));
        assertThrows(RuntimeException.class, () -> db.get(data));
        assertThrows(RuntimeException.class, () -> db.delete(data));
        assertThrows(RuntimeException.class, () -> db.put(data, data, "auth"));
        assertThrows(RuntimeException.class, () -> db.get(data, "auth"));
        assertThrows(RuntimeException.class, () -> db.delete(data, "auth"));
    }

    @Test
    void testGetOnMissingKeyThrowsRuntimeWrapper() {
    }
}