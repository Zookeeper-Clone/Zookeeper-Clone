package server.zookeeper.modules;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.ratis.protocol.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.zookeeper.DB.DataBase;
import server.zookeeper.proto.query.QueryResponse;
import server.zookeeper.proto.query.QueryType;
import server.zookeeper.proto.query.UserQuery;
import server.zookeeper.util.ReservedDirectories;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class QueryHandlerTest {

    private DataBase mockDb;
    private WatchManager mockWatchManager;
    private SessionManager mockSessionManager;
    private QueryHandler handler;
    private MockedStatic<ReservedDirectories> reservedMock;

    @BeforeEach
    public void setUp() {
        mockDb = mock(DataBase.class);
        mockWatchManager = mock(WatchManager.class);
        mockSessionManager = mock(SessionManager.class);
        // Assuming QueryHandler constructor accepts all dependencies now
        handler = new QueryHandler(mockDb, mockSessionManager);
        reservedMock = mockStatic(ReservedDirectories.class);
    }

    @AfterEach
    public void tearDown() {
        reservedMock.close();
    }

    private UserQuery createQuery(QueryType type, String key, String val, String dir) {
        UserQuery.Builder builder = UserQuery.newBuilder()
                .setQueryType(type)
                .setKey(key)
                .setValue(val);
        if (dir != null) {
            builder.setDirectory(dir);
        }
        return builder.build();
    }

    private QueryResponse parseResponse(CompletableFuture<Message> future) throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        return QueryResponse.parseFrom(future.get().getContent().toByteArray());
    }

    @Test
    public void testGetHandlerType() {
        assertEquals("QUERY", handler.getHandlerType());
    }

    @Test
    public void testCanHandleNullPayload() {
        assertFalse(handler.canHandle(null));
    }

    @Test
    public void testCanHandleEmptyPayload() {
        assertFalse(handler.canHandle(new byte[0]));
    }

    @Test
    public void testCanHandleGarbagePayload() {
        assertFalse(handler.canHandle(new byte[]{1, 2, 3, 4, 5}));
    }

    @Test
    public void testCanHandleUnspecifiedQueryType() {
        UserQuery query = UserQuery.newBuilder().setQueryType(QueryType.QUERY_TYPE_UNSPECIFIED).build();
        assertFalse(handler.canHandle(query.toByteArray()));
    }

    @Test
    public void testCanHandleValidGet() {
        UserQuery query = createQuery(QueryType.GET, "k", "v", null);
        assertTrue(handler.canHandle(query.toByteArray()));
    }

    @Test
    public void testCanHandleValidCreate() {
        UserQuery query = createQuery(QueryType.CREATE, "k", "v", null);
        assertTrue(handler.canHandle(query.toByteArray()));
    }

    @Test
    public void testCanHandleValidUpdate() {
        UserQuery query = createQuery(QueryType.UPDATE, "k", "v", null);
        assertTrue(handler.canHandle(query.toByteArray()));
    }

    @Test
    public void testCanHandleValidDelete() {
        UserQuery query = createQuery(QueryType.DELETE, "k", "v", null);
        assertTrue(handler.canHandle(query.toByteArray()));
    }

    @Test
    public void testHandlePayloadParseError() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        CompletableFuture<Message> future = handler.handle(new byte[]{1, 2, 3}, false);
        QueryResponse res = parseResponse(future);
        assertFalse(res.getSuccess());
        assertTrue(res.getErrorMessage().startsWith("Failed to handle query"));
    }

    @Test
    public void testHandleReservedDirectory() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        reservedMock.when(() -> ReservedDirectories.isReserved("sys")).thenReturn(true);
        reservedMock.when(() -> ReservedDirectories.getReservedDirectoryError("sys")).thenReturn("Error Reserved");

        UserQuery query = createQuery(QueryType.GET, "k", "v", "sys");
        CompletableFuture<Message> future = handler.handle(query.toByteArray(), false);
        QueryResponse res = parseResponse(future);

        assertFalse(res.getSuccess());
        assertEquals("Error Reserved", res.getErrorMessage());
    }

    @Test
    public void testHandleReservedDirectoryFalse() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        reservedMock.when(() -> ReservedDirectories.isReserved("usr")).thenReturn(false);
        when(mockDb.get(any(), any())).thenReturn("val".getBytes());

        UserQuery query = createQuery(QueryType.GET, "k", "v", "usr");
        CompletableFuture<Message> future = handler.handle(query.toByteArray(), false);

        assertTrue(parseResponse(future).getSuccess());
    }

    @Test
    public void testHandleInvalidQueryTypeInSwitch() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        UserQuery query = UserQuery.newBuilder().setQueryType(QueryType.QUERY_TYPE_UNSPECIFIED).build();
        CompletableFuture<Message> future = handler.handle(query.toByteArray(), false);
        QueryResponse res = parseResponse(future);

        assertFalse(res.getSuccess());
        assertEquals("Invalid query type", res.getErrorMessage());
    }

    // --- GET Operation Tests ---

    @Test
    public void testGetNoDirectoryFound() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        when(mockDb.get("key".getBytes())).thenReturn("value".getBytes(StandardCharsets.UTF_8));

        UserQuery query = createQuery(QueryType.GET, "key", "", "");
        CompletableFuture<Message> future = handler.handle(query.toByteArray(), false);
        QueryResponse res = parseResponse(future);

        assertTrue(res.getSuccess());
        assertEquals("value", res.getValue());
        verify(mockDb).get("key".getBytes());
    }

    @Test
    public void testGetNoDirectoryNotFound() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        when(mockDb.get("key".getBytes())).thenReturn(null);

        UserQuery query = createQuery(QueryType.GET, "key", "", "");
        CompletableFuture<Message> future = handler.handle(query.toByteArray(), false);
        QueryResponse res = parseResponse(future);

        assertFalse(res.getSuccess());
        assertEquals("__NOT_FOUND__", res.getValue());
        assertEquals("key not found", res.getErrorMessage());
    }

    @Test
    public void testGetWithDirectoryFound() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        when(mockDb.get("key".getBytes(), "dir")).thenReturn("value".getBytes(StandardCharsets.UTF_8));

        UserQuery query = createQuery(QueryType.GET, "key", "", "dir");
        CompletableFuture<Message> future = handler.handle(query.toByteArray(), false);
        QueryResponse res = parseResponse(future);

        assertTrue(res.getSuccess());
        assertEquals("value", res.getValue());
    }

    @Test
    public void testGetWithDirectoryNotFound() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        when(mockDb.get("key".getBytes(), "dir")).thenReturn(null);

        UserQuery query = createQuery(QueryType.GET, "key", "", "dir");
        CompletableFuture<Message> future = handler.handle(query.toByteArray(), false);
        QueryResponse res = parseResponse(future);

        assertFalse(res.getSuccess());
    }

    @Test
    public void testGetEmptyDirectoryStringTreatsAsNull() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        when(mockDb.get("key".getBytes())).thenReturn("val".getBytes());

        UserQuery query = createQuery(QueryType.GET, "key", "", "");
        handler.handle(query.toByteArray(), false).get();

        verify(mockDb).get("key".getBytes());
        verify(mockDb, never()).get(any(), anyString());
    }

    // --- CREATE Operation Tests ---

    @Test
    public void testCreateRequiresMutationFlag() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        UserQuery query = createQuery(QueryType.CREATE, "k", "v", null);
        CompletableFuture<Message> future = handler.handle(query.toByteArray(), false);
        QueryResponse res = parseResponse(future);

        assertFalse(res.getSuccess());
        assertEquals("CREATE operation requires mutation flag", res.getErrorMessage());
    }

    @Test
    public void testCreateNoDirectory() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        UserQuery query = createQuery(QueryType.CREATE, "k", "v", "");
        CompletableFuture<Message> future = handler.handle(query.toByteArray(), true);
        QueryResponse res = parseResponse(future);

        assertTrue(res.getSuccess());
        assertEquals("OK ENTRY CREATED", res.getValue());
        verify(mockDb).put("k".getBytes(), "v".getBytes());
    }

    @Test
    public void testCreateWithDirectory() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        UserQuery query = createQuery(QueryType.CREATE, "k", "v", "dir");
        CompletableFuture<Message> future = handler.handle(query.toByteArray(), true);
        QueryResponse res = parseResponse(future);

        assertTrue(res.getSuccess());
        verify(mockDb).put("k".getBytes(), "v".getBytes(), "dir");
    }

    @Test
    public void testCreateEmptyDirectoryTreatsAsNull() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        UserQuery query = createQuery(QueryType.CREATE, "k", "v", "");
        handler.handle(query.toByteArray(), true).get();

        verify(mockDb).put("k".getBytes(), "v".getBytes());
        verify(mockDb, never()).put(any(), any(), anyString());
    }

    @Test
    public void testCreateEmptyKey() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        UserQuery query = createQuery(QueryType.CREATE, "", "v", null);
        CompletableFuture<Message> future = handler.handle(query.toByteArray(), true);
        QueryResponse res = parseResponse(future);

        assertTrue(res.getSuccess());
        verify(mockDb).put(eq(new byte[0]), eq("v".getBytes()));
    }

    @Test
    public void testCreateEmptyValue() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        UserQuery query = createQuery(QueryType.CREATE, "k", "", null);
        CompletableFuture<Message> future = handler.handle(query.toByteArray(), true);
        QueryResponse res = parseResponse(future);

        assertTrue(res.getSuccess());
        verify(mockDb).put(eq("k".getBytes()), eq(new byte[0]));
    }

    @Test
    public void testCreateFailsWhenKeyExists() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        when(mockDb.get("k".getBytes())).thenReturn("old".getBytes());

        UserQuery query = createQuery(QueryType.CREATE, "k", "v", "");
        CompletableFuture<Message> future = handler.handle(query.toByteArray(), true);
        QueryResponse res = parseResponse(future);

        assertFalse(res.getSuccess());
        assertEquals("Key already exists", res.getErrorMessage());
        assertEquals("old", res.getValue());
        verify(mockDb, never()).put(any(), any());
    }

    // --- UPDATE Operation Tests ---

    @Test
    public void testUpdateRequiresMutationFlag() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        UserQuery query = createQuery(QueryType.UPDATE, "k", "v", null);
        CompletableFuture<Message> future = handler.handle(query.toByteArray(), false);
        QueryResponse res = parseResponse(future);

        assertFalse(res.getSuccess());
        assertEquals("UPDATE operation requires mutation flag", res.getErrorMessage());
    }

    @Test
    public void testUpdateFailsWhenKeyMissing() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        when(mockDb.get("k".getBytes())).thenReturn(null);

        UserQuery query = createQuery(QueryType.UPDATE, "k", "v", "");
        CompletableFuture<Message> future = handler.handle(query.toByteArray(), true);
        QueryResponse res = parseResponse(future);

        assertFalse(res.getSuccess());
        assertEquals("Key does not exist", res.getErrorMessage());
        assertEquals("__NOT_FOUND__", res.getValue());
        verify(mockDb, never()).put(any(), any());
    }

    @Test
    public void testUpdateNoDirectory() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        when(mockDb.get("k".getBytes())).thenReturn("old".getBytes());

        UserQuery query = createQuery(QueryType.UPDATE, "k", "new", "");
        CompletableFuture<Message> future = handler.handle(query.toByteArray(), true);
        QueryResponse res = parseResponse(future);

        assertTrue(res.getSuccess());
        assertEquals("OK ENTRY UPDATED", res.getValue());
        verify(mockDb).put("k".getBytes(), "new".getBytes());
    }

    @Test
    public void testUpdateWithDirectory() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        when(mockDb.get("k".getBytes(), "dir")).thenReturn("old".getBytes());

        UserQuery query = createQuery(QueryType.UPDATE, "k", "new", "dir");
        CompletableFuture<Message> future = handler.handle(query.toByteArray(), true);
        QueryResponse res = parseResponse(future);

        assertTrue(res.getSuccess());
        verify(mockDb).put("k".getBytes(), "new".getBytes(), "dir");
    }

    // --- DELETE Operation Tests ---

    @Test
    public void testDeleteRequiresMutationFlag() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        UserQuery query = createQuery(QueryType.DELETE, "k", "", null);
        CompletableFuture<Message> future = handler.handle(query.toByteArray(), false);
        QueryResponse res = parseResponse(future);

        assertFalse(res.getSuccess());
        assertEquals("DELETE operation requires mutation flag", res.getErrorMessage());
    }

    @Test
    public void testDeleteNoDirectoryKeyNotFound() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        when(mockDb.get("k".getBytes())).thenReturn(null);

        UserQuery query = createQuery(QueryType.DELETE, "k", "", "");
        CompletableFuture<Message> future = handler.handle(query.toByteArray(), true);
        QueryResponse res = parseResponse(future);

        assertFalse(res.getSuccess());
        assertEquals("Key does not exist", res.getErrorMessage());
        verify(mockDb, never()).delete(any());
    }

    @Test
    public void testDeleteNoDirectoryKeyExists() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        when(mockDb.get("k".getBytes())).thenReturn("v".getBytes());

        UserQuery query = createQuery(QueryType.DELETE, "k", "", "");
        CompletableFuture<Message> future = handler.handle(query.toByteArray(), true);
        QueryResponse res = parseResponse(future);

        assertTrue(res.getSuccess());
        assertEquals("OK ENTRY DELETED", res.getValue());
        verify(mockDb).delete("k".getBytes());
    }

    @Test
    public void testDeleteWithDirectoryKeyNotFound() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        when(mockDb.get("k".getBytes(), "dir")).thenReturn(null);

        UserQuery query = createQuery(QueryType.DELETE, "k", "", "dir");
        CompletableFuture<Message> future = handler.handle(query.toByteArray(), true);
        QueryResponse res = parseResponse(future);

        assertFalse(res.getSuccess());
        verify(mockDb, never()).delete(any(), any());
    }

    @Test
    public void testDeleteWithDirectoryKeyExists() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        when(mockDb.get("k".getBytes(), "dir")).thenReturn("v".getBytes());

        UserQuery query = createQuery(QueryType.DELETE, "k", "", "dir");
        CompletableFuture<Message> future = handler.handle(query.toByteArray(), true);
        QueryResponse res = parseResponse(future);

        assertTrue(res.getSuccess());
        verify(mockDb).delete("k".getBytes(), "dir");
    }

    @Test
    public void testDeleteEmptyDirectoryKeyExists() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        when(mockDb.get("k".getBytes())).thenReturn("v".getBytes());

        UserQuery query = createQuery(QueryType.DELETE, "k", "", "");
        handler.handle(query.toByteArray(), true).get();

        verify(mockDb).delete("k".getBytes());
    }

    @Test
    public void testDeleteEmptyKeyExists() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        when(mockDb.get(new byte[0])).thenReturn("v".getBytes());

        UserQuery query = createQuery(QueryType.DELETE, "", "", null);
        handler.handle(query.toByteArray(), true).get();

        verify(mockDb).delete(new byte[0]);
    }

    @Test
    public void testDeleteEmptyKeyNotFound() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        when(mockDb.get(new byte[0])).thenReturn(null);

        UserQuery query = createQuery(QueryType.DELETE, "", "", null);
        CompletableFuture<Message> future = handler.handle(query.toByteArray(), true);
        QueryResponse res = parseResponse(future);

        assertFalse(res.getSuccess());
    }

    // --- DB Exception Tests ---

    @Test
    public void testDbExceptionOnGet() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        when(mockDb.get(any())).thenThrow(new RuntimeException("DB Error"));

        UserQuery query = createQuery(QueryType.GET, "k", "v", null);
        CompletableFuture<Message> future = handler.handle(query.toByteArray(), false);
        QueryResponse res = parseResponse(future);

        assertFalse(res.getSuccess());
        assertTrue(res.getErrorMessage().contains("DB Error"));
    }

    @Test
    public void testDbExceptionOnCreate() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        doThrow(new RuntimeException("Write Error")).when(mockDb).put(any(), any());

        UserQuery query = createQuery(QueryType.CREATE, "k", "v", null);
        CompletableFuture<Message> future = handler.handle(query.toByteArray(), true);
        QueryResponse res = parseResponse(future);

        assertFalse(res.getSuccess());
        assertTrue(res.getErrorMessage().contains("Write Error"));
    }

    @Test
    public void testDbExceptionOnUpdate() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        when(mockDb.get(any())).thenReturn("old".getBytes());
        doThrow(new RuntimeException("Update Error")).when(mockDb).put(any(), any());

        UserQuery query = createQuery(QueryType.UPDATE, "k", "v", null);
        CompletableFuture<Message> future = handler.handle(query.toByteArray(), true);
        QueryResponse res = parseResponse(future);

        assertFalse(res.getSuccess());
        assertTrue(res.getErrorMessage().contains("Update Error"));
    }

    @Test
    public void testDbExceptionOnDeleteCheck() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        when(mockDb.get(any())).thenThrow(new RuntimeException("Check Error"));

        UserQuery query = createQuery(QueryType.DELETE, "k", "v", null);
        CompletableFuture<Message> future = handler.handle(query.toByteArray(), true);
        QueryResponse res = parseResponse(future);

        assertFalse(res.getSuccess());
        assertTrue(res.getErrorMessage().contains("Check Error"));
    }

    @Test
    public void testDbExceptionOnDeleteAction() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        when(mockDb.get(any())).thenReturn("v".getBytes());
        doThrow(new RuntimeException("Delete Action Error")).when(mockDb).delete(any());

        UserQuery query = createQuery(QueryType.DELETE, "k", "v", null);
        CompletableFuture<Message> future = handler.handle(query.toByteArray(), true);
        QueryResponse res = parseResponse(future);

        assertFalse(res.getSuccess());
        assertTrue(res.getErrorMessage().contains("Delete Action Error"));
    }

    @Test
    public void testGetUtf8Values() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        String utf8Str = "こんにちは";
        when(mockDb.get("k".getBytes())).thenReturn(utf8Str.getBytes(StandardCharsets.UTF_8));

        UserQuery query = createQuery(QueryType.GET, "k", "", null);
        CompletableFuture<Message> future = handler.handle(query.toByteArray(), false);
        QueryResponse res = parseResponse(future);

        assertEquals(utf8Str, res.getValue());
    }

    @Test
    public void testCreateUtf8Values() throws ExecutionException, InterruptedException {
        String utf8Str = "😊";
        UserQuery query = createQuery(QueryType.CREATE, "k", utf8Str, null);
        handler.handle(query.toByteArray(), true).get();

        verify(mockDb).put(eq("k".getBytes()), eq(utf8Str.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void testReservedDirectoryCheckThrowsException() throws InvalidProtocolBufferException, ExecutionException, InterruptedException {
        reservedMock.when(() -> ReservedDirectories.isReserved(any())).thenThrow(new RuntimeException("Reserved Check Fail"));

        UserQuery query = createQuery(QueryType.GET, "k", "v", "sys");
        CompletableFuture<Message> future = handler.handle(query.toByteArray(), false);
        QueryResponse res = parseResponse(future);

        assertFalse(res.getSuccess());
        assertTrue(res.getErrorMessage().contains("Reserved Check Fail"));
    }

    @Test
    public void testCreateEphemeralEntry() throws Exception {
        String key = "ephemeralKey";
        String value = "ephemeralValue";
        String sessionToken = "session123";
        String directory = "temp";

        UserQuery query = UserQuery.newBuilder()
                .setQueryType(QueryType.CREATE)
                .setKey(key)
                .setValue(value)
                .setDirectory(directory)
                .setSessionToken(sessionToken)
                .setIsEphemeral(true)
                .build();

        CompletableFuture<Message> future = handler.handle(query.toByteArray(), true);
        QueryResponse response = parseResponse(future);

        assertTrue(response.getSuccess());
        verify(mockDb).put(eq(key.getBytes()), eq(value.getBytes()), eq(directory));
        verify(mockSessionManager).addEphemeralEntry(sessionToken, key, directory);
    }
}