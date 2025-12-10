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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class QueryHandlerTest {

    private DataBase mockDb;
    private QueryHandler handler;
    private MockedStatic<ReservedDirectories> reservedMock;

    @BeforeEach
    public void setUp() {
        mockDb = mock(DataBase.class);
        handler = new QueryHandler(mockDb);
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

    private QueryResponse parseResponse(Message msg) throws InvalidProtocolBufferException {
        return QueryResponse.parseFrom(msg.getContent().toByteArray());
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
    public void testCanHandleValidWrite() {
        UserQuery query = createQuery(QueryType.WRITE, "k", "v", null);
        assertTrue(handler.canHandle(query.toByteArray()));
    }

    @Test
    public void testCanHandleValidDelete() {
        UserQuery query = createQuery(QueryType.DELETE, "k", "v", null);
        assertTrue(handler.canHandle(query.toByteArray()));
    }

    @Test
    public void testHandlePayloadParseError() throws InvalidProtocolBufferException {
        Message msg = handler.handle(new byte[]{1, 2, 3}, false);
        QueryResponse res = parseResponse(msg);
        assertFalse(res.getSuccess());
        assertTrue(res.getErrorMessage().startsWith("Failed to handle query"));
    }

    @Test
    public void testHandleReservedDirectory() throws InvalidProtocolBufferException {
        reservedMock.when(() -> ReservedDirectories.isReserved("sys")).thenReturn(true);
        reservedMock.when(() -> ReservedDirectories.getReservedDirectoryError("sys")).thenReturn("Error Reserved");

        UserQuery query = createQuery(QueryType.GET, "k", "v", "sys");
        Message msg = handler.handle(query.toByteArray(), false);
        QueryResponse res = parseResponse(msg);

        assertFalse(res.getSuccess());
        assertEquals("Error Reserved", res.getErrorMessage());
    }

    @Test
    public void testHandleReservedDirectoryFalse() throws InvalidProtocolBufferException {
        reservedMock.when(() -> ReservedDirectories.isReserved("usr")).thenReturn(false);
        when(mockDb.get(any(), any())).thenReturn("val".getBytes());

        UserQuery query = createQuery(QueryType.GET, "k", "v", "usr");
        Message msg = handler.handle(query.toByteArray(), false);

        assertTrue(parseResponse(msg).getSuccess());
    }

    @Test
    public void testHandleInvalidQueryTypeInSwitch() throws InvalidProtocolBufferException {
        UserQuery query = UserQuery.newBuilder().setQueryType(QueryType.QUERY_TYPE_UNSPECIFIED).build();
        Message msg = handler.handle(query.toByteArray(), false);
        QueryResponse res = parseResponse(msg);

        assertFalse(res.getSuccess());
        assertEquals("Invalid query type", res.getErrorMessage());
    }

    @Test
    public void testGetNoDirectoryFound() throws InvalidProtocolBufferException {
        when(mockDb.get("key".getBytes())).thenReturn("value".getBytes(StandardCharsets.UTF_8));

        UserQuery query = createQuery(QueryType.GET, "key", "", "");
        Message msg = handler.handle(query.toByteArray(), false);
        QueryResponse res = parseResponse(msg);

        assertTrue(res.getSuccess());
        assertEquals("value", res.getValue());
        verify(mockDb).get("key".getBytes());
    }

    @Test
    public void testGetNoDirectoryNotFound() throws InvalidProtocolBufferException {
        when(mockDb.get("key".getBytes())).thenReturn(null);

        UserQuery query = createQuery(QueryType.GET, "key", "", "");
        Message msg = handler.handle(query.toByteArray(), false);
        QueryResponse res = parseResponse(msg);

        assertFalse(res.getSuccess());
        assertEquals("__NOT_FOUND__", res.getValue());
        assertEquals("key not found", res.getErrorMessage());
    }

    @Test
    public void testGetWithDirectoryFound() throws InvalidProtocolBufferException {
        when(mockDb.get("key".getBytes(), "dir")).thenReturn("value".getBytes(StandardCharsets.UTF_8));

        UserQuery query = createQuery(QueryType.GET, "key", "", "dir");
        Message msg = handler.handle(query.toByteArray(), false);
        QueryResponse res = parseResponse(msg);

        assertTrue(res.getSuccess());
        assertEquals("value", res.getValue());
    }

    @Test
    public void testGetWithDirectoryNotFound() throws InvalidProtocolBufferException {
        when(mockDb.get("key".getBytes(), "dir")).thenReturn(null);

        UserQuery query = createQuery(QueryType.GET, "key", "", "dir");
        Message msg = handler.handle(query.toByteArray(), false);
        QueryResponse res = parseResponse(msg);

        assertFalse(res.getSuccess());
    }

    @Test
    public void testGetEmptyDirectoryStringTreatsAsNull() throws InvalidProtocolBufferException {
        when(mockDb.get("key".getBytes())).thenReturn("val".getBytes());

        UserQuery query = createQuery(QueryType.GET, "key", "", "");
        handler.handle(query.toByteArray(), false);

        verify(mockDb).get("key".getBytes());
        verify(mockDb, never()).get(any(), anyString());
    }

    @Test
    public void testWriteRequiresMutationFlag() throws InvalidProtocolBufferException {
        UserQuery query = createQuery(QueryType.WRITE, "k", "v", null);
        Message msg = handler.handle(query.toByteArray(), false);
        QueryResponse res = parseResponse(msg);

        assertFalse(res.getSuccess());
        assertEquals("WRITE operation requires mutation flag", res.getErrorMessage());
    }

    @Test
    public void testWriteNoDirectory() throws InvalidProtocolBufferException {
        UserQuery query = createQuery(QueryType.WRITE, "k", "v", "");
        Message msg = handler.handle(query.toByteArray(), true);
        QueryResponse res = parseResponse(msg);

        assertTrue(res.getSuccess());
        assertEquals("OK ENTRY ADDED", res.getValue());
        verify(mockDb).put("k".getBytes(), "v".getBytes());
    }

    @Test
    public void testWriteWithDirectory() throws InvalidProtocolBufferException {
        UserQuery query = createQuery(QueryType.WRITE, "k", "v", "dir");
        Message msg = handler.handle(query.toByteArray(), true);

        assertTrue(parseResponse(msg).getSuccess());
        verify(mockDb).put("k".getBytes(), "v".getBytes(), "dir");
    }

    @Test
    public void testWriteEmptyDirectoryTreatsAsNull() throws InvalidProtocolBufferException {
        UserQuery query = createQuery(QueryType.WRITE, "k", "v", "");
        handler.handle(query.toByteArray(), true);

        verify(mockDb).put("k".getBytes(), "v".getBytes());
        verify(mockDb, never()).put(any(), any(), anyString());
    }

    @Test
    public void testWriteEmptyKey() throws InvalidProtocolBufferException {
        UserQuery query = createQuery(QueryType.WRITE, "", "v", null);
        Message msg = handler.handle(query.toByteArray(), true);

        assertTrue(parseResponse(msg).getSuccess());
        verify(mockDb).put(eq(new byte[0]), eq("v".getBytes()));
    }

    @Test
    public void testWriteEmptyValue() throws InvalidProtocolBufferException {
        UserQuery query = createQuery(QueryType.WRITE, "k", "", null);
        Message msg = handler.handle(query.toByteArray(), true);

        assertTrue(parseResponse(msg).getSuccess());
        verify(mockDb).put(eq("k".getBytes()), eq(new byte[0]));
    }

    @Test
    public void testDeleteRequiresMutationFlag() throws InvalidProtocolBufferException {
        UserQuery query = createQuery(QueryType.DELETE, "k", "", null);
        Message msg = handler.handle(query.toByteArray(), false);

        assertFalse(parseResponse(msg).getSuccess());
        assertEquals("DELETE operation requires mutation flag", parseResponse(msg).getErrorMessage());
    }

    @Test
    public void testDeleteNoDirectoryKeyNotFound() throws InvalidProtocolBufferException {
        when(mockDb.get("k".getBytes())).thenReturn(null);

        UserQuery query = createQuery(QueryType.DELETE, "k", "", "");
        Message msg = handler.handle(query.toByteArray(), true);
        QueryResponse res = parseResponse(msg);

        assertFalse(res.getSuccess());
        assertEquals("Key does not exist", res.getErrorMessage());
        verify(mockDb, never()).delete(any());
    }

    @Test
    public void testDeleteNoDirectoryKeyExists() throws InvalidProtocolBufferException {
        when(mockDb.get("k".getBytes())).thenReturn("v".getBytes());

        UserQuery query = createQuery(QueryType.DELETE, "k", "", "");
        Message msg = handler.handle(query.toByteArray(), true);
        QueryResponse res = parseResponse(msg);

        assertTrue(res.getSuccess());
        assertEquals("OK ENTRY DELETED", res.getValue());
        verify(mockDb).delete("k".getBytes());
    }

    @Test
    public void testDeleteWithDirectoryKeyNotFound() throws InvalidProtocolBufferException {
        when(mockDb.get("k".getBytes(), "dir")).thenReturn(null);

        UserQuery query = createQuery(QueryType.DELETE, "k", "", "dir");
        Message msg = handler.handle(query.toByteArray(), true);

        assertFalse(parseResponse(msg).getSuccess());
        verify(mockDb, never()).delete(any(), any());
    }

    @Test
    public void testDeleteWithDirectoryKeyExists() throws InvalidProtocolBufferException {
        when(mockDb.get("k".getBytes(), "dir")).thenReturn("v".getBytes());

        UserQuery query = createQuery(QueryType.DELETE, "k", "", "dir");
        Message msg = handler.handle(query.toByteArray(), true);

        assertTrue(parseResponse(msg).getSuccess());
        verify(mockDb).delete("k".getBytes(), "dir");
    }

    @Test
    public void testDeleteEmptyDirectoryKeyExists() throws InvalidProtocolBufferException {
        when(mockDb.get("k".getBytes())).thenReturn("v".getBytes());

        UserQuery query = createQuery(QueryType.DELETE, "k", "", "");
        handler.handle(query.toByteArray(), true);

        verify(mockDb).delete("k".getBytes());
    }

    @Test
    public void testDeleteEmptyKeyExists() throws InvalidProtocolBufferException {
        when(mockDb.get(new byte[0])).thenReturn("v".getBytes());

        UserQuery query = createQuery(QueryType.DELETE, "", "", null);
        handler.handle(query.toByteArray(), true);

        verify(mockDb).delete(new byte[0]);
    }

    @Test
    public void testDeleteEmptyKeyNotFound() throws InvalidProtocolBufferException {
        when(mockDb.get(new byte[0])).thenReturn(null);

        UserQuery query = createQuery(QueryType.DELETE, "", "", null);
        Message msg = handler.handle(query.toByteArray(), true);

        assertFalse(parseResponse(msg).getSuccess());
    }

    @Test
    public void testDbExceptionOnGet() throws InvalidProtocolBufferException {
        when(mockDb.get(any())).thenThrow(new RuntimeException("DB Error"));

        UserQuery query = createQuery(QueryType.GET, "k", "v", null);
        Message msg = handler.handle(query.toByteArray(), false);
        QueryResponse res = parseResponse(msg);

        assertFalse(res.getSuccess());
        assertTrue(res.getErrorMessage().contains("DB Error"));
    }

    @Test
    public void testDbExceptionOnWrite() throws InvalidProtocolBufferException {
        doThrow(new RuntimeException("Write Error")).when(mockDb).put(any(), any());

        UserQuery query = createQuery(QueryType.WRITE, "k", "v", null);
        Message msg = handler.handle(query.toByteArray(), true);
        QueryResponse res = parseResponse(msg);

        assertFalse(res.getSuccess());
        assertTrue(res.getErrorMessage().contains("Write Error"));
    }

    @Test
    public void testDbExceptionOnDeleteCheck() throws InvalidProtocolBufferException {
        when(mockDb.get(any())).thenThrow(new RuntimeException("Check Error"));

        UserQuery query = createQuery(QueryType.DELETE, "k", "v", null);
        Message msg = handler.handle(query.toByteArray(), true);
        QueryResponse res = parseResponse(msg);

        assertFalse(res.getSuccess());
        assertTrue(res.getErrorMessage().contains("Check Error"));
    }

    @Test
    public void testDbExceptionOnDeleteAction() throws InvalidProtocolBufferException {
        when(mockDb.get(any())).thenReturn("v".getBytes());
        doThrow(new RuntimeException("Delete Action Error")).when(mockDb).delete(any());

        UserQuery query = createQuery(QueryType.DELETE, "k", "v", null);
        Message msg = handler.handle(query.toByteArray(), true);
        QueryResponse res = parseResponse(msg);

        assertFalse(res.getSuccess());
        assertTrue(res.getErrorMessage().contains("Delete Action Error"));
    }

    @Test
    public void testGetUtf8Values() throws InvalidProtocolBufferException {
        String utf8Str = "こんにちは";
        when(mockDb.get("k".getBytes())).thenReturn(utf8Str.getBytes(StandardCharsets.UTF_8));

        UserQuery query = createQuery(QueryType.GET, "k", "", null);
        Message msg = handler.handle(query.toByteArray(), false);
        QueryResponse res = parseResponse(msg);

        assertEquals(utf8Str, res.getValue());
    }

    @Test
    public void testWriteUtf8Values() {
        String utf8Str = "😊";
        UserQuery query = createQuery(QueryType.WRITE, "k", utf8Str, null);
        handler.handle(query.toByteArray(), true);

        verify(mockDb).put(eq("k".getBytes()), eq(utf8Str.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void testReservedDirectoryCheckThrowsException() throws InvalidProtocolBufferException {
        reservedMock.when(() -> ReservedDirectories.isReserved(any())).thenThrow(new RuntimeException("Reserved Check Fail"));

        UserQuery query = createQuery(QueryType.GET, "k", "v", "sys");
        Message msg = handler.handle(query.toByteArray(), false);
        QueryResponse res = parseResponse(msg);

        assertFalse(res.getSuccess());
        assertTrue(res.getErrorMessage().contains("Reserved Check Fail"));
    }
}