package server.zookeeper.modules;

import org.apache.ratis.protocol.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;


@DisplayName("QueryHandlerAdapter Tests")
class QueryHandlerAdapterTest {

    @Mock
    private QueryHandler mockQueryHandler;

    private QueryHandlerAdapter adapter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        adapter = new QueryHandlerAdapter(mockQueryHandler);
    }

    @Test
    @DisplayName("Constructor should throw IllegalArgumentException when QueryHandler is null")
    void constructor_withNullQueryHandler_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new QueryHandlerAdapter(null));
    }

    @Test
    @DisplayName("getHandlerType should return QUERY")
    void getHandlerType_returnsQuery() {
        assertEquals("QUERY", adapter.getHandlerType());
    }

    @Test
    @DisplayName("handle should delegate mutation to QueryHandler.handleMutation")
    void handle_withMutation_delegatesToHandleMutation() {
        String query = "PUT key=value";
        byte[] payload = query.getBytes(StandardCharsets.UTF_8);
        Message expectedResponse = Message.valueOf("OK ENTRY ADDED");
        when(mockQueryHandler.handleMutation(query)).thenReturn(expectedResponse);

        Message result = adapter.handle(payload, true);

        verify(mockQueryHandler, times(1)).handleMutation(query);
        verify(mockQueryHandler, never()).handleQuery(anyString());
        assertEquals(expectedResponse, result);
    }

    @Test
    @DisplayName("handle should delegate query to QueryHandler.handleQuery")
    void handle_withQuery_delegatesToHandleQuery() {
        String query = "GET mykey";
        byte[] payload = query.getBytes(StandardCharsets.UTF_8);
        Message expectedResponse = Message.valueOf("myvalue");
        when(mockQueryHandler.handleQuery(query)).thenReturn(expectedResponse);

        Message result = adapter.handle(payload, false);

        verify(mockQueryHandler, times(1)).handleQuery(query);
        verify(mockQueryHandler, never()).handleMutation(anyString());
        assertEquals(expectedResponse, result);
    }

    @Test
    @DisplayName("handle should throw RuntimeException when QueryHandler throws exception")
    void handle_whenQueryHandlerThrowsException_throwsRuntimeException() {
        String query = "PUT key=value";
        byte[] payload = query.getBytes(StandardCharsets.UTF_8);
        RuntimeException cause = new RuntimeException("Database error");
        when(mockQueryHandler.handleMutation(query)).thenThrow(cause);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> adapter.handle(payload, true));
        assertTrue(thrown.getMessage().contains("Failed to handle query"));
        assertEquals(cause, thrown.getCause());
    }

    @Test
    @DisplayName("canHandle should return true for valid command")
    void canHandle_withValidCommand_returnsTrue() {
        byte[] payload = "PUT key=value".getBytes(StandardCharsets.UTF_8);
        assertTrue(adapter.canHandle(payload));

        payload = "GET mykey".getBytes(StandardCharsets.UTF_8);
        assertTrue(adapter.canHandle(payload));

         payload = "DELETE mykey".getBytes(StandardCharsets.UTF_8);
        assertTrue(adapter.canHandle(payload));
    }

    @Test
    @DisplayName("canHandle should return false for invalid payload")
    void canHandle_withNullPayload_returnsFalse() {
        assertFalse(adapter.canHandle(null));
        assertFalse(adapter.canHandle(new byte[0]));
        byte[] payload = "INVALID COMMAND".getBytes(StandardCharsets.UTF_8);
        assertFalse(adapter.canHandle(payload));
        payload = "PUT".getBytes(StandardCharsets.UTF_8);
        assertFalse(adapter.canHandle(payload));
    }

    @Test
    @DisplayName("handle should handle query with IN clause")
    void handle_withInClause_delegatesCorrectly() {
        String query = "GET mykey IN mydirectory";
        byte[] payload = query.getBytes(StandardCharsets.UTF_8);
        Message expectedResponse = Message.valueOf("myvalue");
        when(mockQueryHandler.handleQuery(query)).thenReturn(expectedResponse);

        Message result = adapter.handle(payload, false);

        verify(mockQueryHandler).handleQuery(query);
        assertEquals(expectedResponse, result);
    }
}