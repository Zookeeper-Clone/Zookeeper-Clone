package server.zookeeper.modules;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.ratis.protocol.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import server.zookeeper.proto.MessageType;
import server.zookeeper.proto.MessageWrapper;
import server.zookeeper.proto.ResponseWrapper;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class MessageRouterTest {
    @Mock
    private MessageHandler mockQueryHandler;

    @Mock
    private MessageHandler mockAuthHandler;

    @Mock
    private SessionManager mockSessionManager;
    private MessageRouter router;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        when(mockQueryHandler.getHandlerType()).thenReturn("QUERY");
        when(mockQueryHandler.handle(any(), anyBoolean()))
                .thenReturn(Message.valueOf("QUERY_RESPONSE"));

        when(mockAuthHandler.getHandlerType()).thenReturn("AUTH");
        when(mockAuthHandler.handle(any(), anyBoolean()))
                .thenReturn(Message.valueOf("AUTH_RESPONSE"));

        router = new MessageRouter(mockQueryHandler, mockSessionManager);
        router.registerHandler(MessageType.QUERY, mockQueryHandler);
        router.registerHandler(MessageType.AUTH, mockAuthHandler);
    }

    @Test
    void testRouteWrappedQueryMessage() {
        String token = "valid-token";
        when(mockSessionManager.validateSession(token)).thenReturn(true);

        // Create wrapped QUERY message
        MessageWrapper wrapper = MessageWrapper.newBuilder()
                .setType(MessageType.QUERY)
                .setPayload(ByteString.copyFromUtf8("GET key"))
                .setSessionToken(token)
                .build();

        Message result = router.route(wrapper.toByteArray(), false);

        assertEquals("QUERY_RESPONSE", result.getContent().toStringUtf8());
        verify(mockQueryHandler, times(1)).handle(any(), eq(false));
        verify(mockSessionManager).validateSession(token);
    }

    @Test
    void testRouteWrappedAuthMessage() {
        // Create wrapped AUTH message
        MessageWrapper wrapper = MessageWrapper.newBuilder()
                .setType(MessageType.AUTH)
                .setPayload(ByteString.copyFromUtf8("AUTH_PAYLOAD"))
                .build();

        when(mockAuthHandler.handle(any(), anyBoolean()))
                .thenReturn(Message.valueOf("AUTH_RESPONSE"));

        Message result = router.route(wrapper.toByteArray(), true);

        assertEquals("AUTH_RESPONSE", result.getContent().toStringUtf8());
        verify(mockAuthHandler, times(1)).handle(any(), eq(true));
        // Auth messages do not require session validation
        //! I assume that auth messages will get authenticated in the authHandler
        verify(mockSessionManager, never()).validateSession(anyString());

    }

    @Test
    void testUnknownMessageTypeReturnsError() throws InvalidProtocolBufferException {
        // Create wrapper with UNSPECIFIED type
        MessageWrapper wrapper = MessageWrapper.newBuilder()
                .setType(MessageType.UNSPECIFIED)
                .setPayload(ByteString.copyFromUtf8("DATA"))
                .build();

        Message result = router.route(wrapper.toByteArray(), false);
        assertFalse(ResponseWrapper.parseFrom(result.getContent().toByteArray()).getSuccess());
    }

    @Test
    void testRouteWrappedQueryMessageUnauthorized() {
        String token = "invalid-token";
        // Mock failed validation
        when(mockSessionManager.validateSession(token)).thenReturn(false);

        MessageWrapper wrapper = MessageWrapper.newBuilder()
                .setType(MessageType.QUERY)
                .setPayload(ByteString.copyFromUtf8("GET key"))
                .setSessionToken(token)
                .build();

        Message result = router.route(wrapper.toByteArray(), false);

        // Should return error and NOT call handler
        assertTrue(result.getContent().toStringUtf8().contains("Unauthorized"));
        verify(mockQueryHandler, never()).handle(any(), anyBoolean());
    }
}
