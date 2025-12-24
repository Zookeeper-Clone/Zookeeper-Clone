package server.zookeeper.modules;

import org.apache.ratis.protocol.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.zookeeper.util.ReservedDirectories;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WatchManagerTest {

    private WatchManager watchManager;
    private MockedStatic<ReservedDirectories> reservedMock;
    private static final String TEST_KEY = "node1";
    private static final String TEST_DIR = "/app/configs";
    private static final byte[] TEST_DATA = "new_value".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() {
        watchManager = new WatchManager();
        reservedMock = mockStatic(ReservedDirectories.class);
    }

    @AfterEach
    void tearDown() {
        reservedMock.close();
    }

    @Test
    @DisplayName("Should successfully complete a future when a watch is triggered")
    void shouldCompleteFutureOnTrigger() throws Exception {
        reservedMock.when(() -> ReservedDirectories.isReserved(TEST_DIR)).thenReturn(false);
        CompletableFuture<Message> watch = watchManager.addWatch(TEST_KEY, TEST_DIR);

        assertFalse(watch.isDone(), "Watch should be pending initially");

        watchManager.triggerNotify(TEST_KEY, TEST_DIR, TEST_DATA);

        assertTrue(watch.isDone(), "Watch should be completed after trigger");
        Message result = watch.get(1, TimeUnit.SECONDS);
        assertArrayEquals(TEST_DATA, result.getContent().toByteArray());
    }

    @Test
    @DisplayName("Should support multiple watchers for the same key and directory")
    void shouldSupportMultipleWatchers() throws Exception {
        reservedMock.when(() -> ReservedDirectories.isReserved(TEST_DIR)).thenReturn(false);
        CompletableFuture<Message> watcher1 = watchManager.addWatch(TEST_KEY, TEST_DIR);
        CompletableFuture<Message> watcher2 = watchManager.addWatch(TEST_KEY, TEST_DIR);

        watchManager.triggerNotify(TEST_KEY, TEST_DIR, TEST_DATA);

        assertTrue(watcher1.isDone());
        assertTrue(watcher2.isDone());

        assertArrayEquals(TEST_DATA, watcher1.get().getContent().toByteArray());
        assertArrayEquals(TEST_DATA, watcher2.get().getContent().toByteArray());
    }

    @Test
    @DisplayName("Should only trigger watches for the matching key and directory")
    void shouldIsolateWatchesByKey() {
        reservedMock.when(() -> ReservedDirectories.isReserved(anyString())).thenReturn(false);

        CompletableFuture<Message> targetWatch = watchManager.addWatch(TEST_KEY, TEST_DIR);
        CompletableFuture<Message> otherKeyWatch = watchManager.addWatch("otherKey", TEST_DIR);
        CompletableFuture<Message> otherDirWatch = watchManager.addWatch(TEST_KEY, "/other/dir");

        watchManager.triggerNotify(TEST_KEY, TEST_DIR, TEST_DATA);

        assertTrue(targetWatch.isDone(), "Target watch should be triggered");
        assertFalse(otherKeyWatch.isDone(), "Watch for different key should remain pending");
        assertFalse(otherDirWatch.isDone(), "Watch for different directory should remain pending");
    }

    @Test
    @DisplayName("Should remove watchers after they are triggered (one-shot behavior)")
    void shouldRemoveWatchersAfterTrigger() {
        reservedMock.when(() -> ReservedDirectories.isReserved(TEST_DIR)).thenReturn(false);
        CompletableFuture<Message> watch = watchManager.addWatch(TEST_KEY, TEST_DIR);

        watchManager.triggerNotify(TEST_KEY, TEST_DIR, TEST_DATA);
        assertTrue(watch.isDone());

        // Triggering again with different data
        byte[] newerData = "even_newer".getBytes(StandardCharsets.UTF_8);
        watchManager.triggerNotify(TEST_KEY, TEST_DIR, newerData);

        // A new watch added now should NOT have been affected by the previous triggers
        CompletableFuture<Message> newWatch = watchManager.addWatch(TEST_KEY, TEST_DIR);
        assertFalse(newWatch.isDone());
    }

    @Test
    @DisplayName("Should handle triggerNotify when no watchers exist")
    void shouldHandleTriggerWithNoWatchers() {
        assertDoesNotThrow(() -> {
            watchManager.triggerNotify("nonexistent", "/none", "data".getBytes());
        });
    }

    @Test
    @DisplayName("Should respect value-based equality for WatchKey")
    void shouldRespectValueEquality() throws Exception {
        reservedMock.when(() -> ReservedDirectories.isReserved(anyString())).thenReturn(false);

        // Create strings dynamically to ensure they aren't the same reference
        String dir1 = new String("/dir");
        String dir2 = new String("/dir");

        CompletableFuture<Message> watch = watchManager.addWatch("k", dir1);
        watchManager.triggerNotify("k", dir2, TEST_DATA);

        assertTrue(watch.isDone(), "Watch should trigger because directory values match");
    }

    @Test
    @DisplayName("Should throw exception or return error when watching a reserved directory")
    void shouldHandleReservedDirectory() throws ExecutionException, InterruptedException {
        String reservedDir = "/sys";
        reservedMock.when(() -> ReservedDirectories.isReserved(reservedDir)).thenReturn(true);
        reservedMock.when(() -> ReservedDirectories.getReservedDirectoryError(reservedDir)).thenReturn("Reserved");

        Message msg = watchManager.addWatch("l", "/sys").get();
        assertEquals(msg.getContent().toStringUtf8(), "Reserved");
    }
}