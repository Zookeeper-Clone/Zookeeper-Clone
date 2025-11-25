package server.zookeeper.modules;

import com.google.protobuf.ByteString;
import org.apache.ratis.protocol.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import server.zookeeper.proto.MessageType;
import server.zookeeper.proto.MessageWrapper;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class MessageRouterTest {
    @Mock
    private MessageHandler mockQueryHandler;

    @Mock
    private MessageHandler mockAuthHandler;

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

        router = new MessageRouter(mockQueryHandler);
        router.registerHandler(MessageType.QUERY, mockQueryHandler);
        router.registerHandler(MessageType.AUTH, mockAuthHandler);
    }

    @Test
    void testRouteWrappedQueryMessage() {
        // Create wrapped QUERY message
        MessageWrapper wrapper = MessageWrapper.newBuilder()
                .setType(MessageType.QUERY)
                .setPayload(ByteString.copyFromUtf8("GET key"))
                .build();

        Message result = router.route(wrapper.toByteArray(), false);

        assertEquals("QUERY_RESPONSE", result.getContent().toStringUtf8());
        verify(mockQueryHandler, times(1)).handle(any(), eq(false));
    }

    @Test
    void testRouteWrappedAuthMessage() {
        // Create wrapped AUTH message
        MessageWrapper wrapper = MessageWrapper.newBuilder()
                .setType(MessageType.AUTH)
                .setPayload(ByteString.copyFromUtf8("AUTH_PAYLOAD"))
                .build();

        Message result = router.route(wrapper.toByteArray(), true);

        assertEquals("AUTH_RESPONSE", result.getContent().toStringUtf8());
        verify(mockAuthHandler, times(1)).handle(any(), eq(true));
    }

    @Test
    void testBackwardCompatibilityWithRawString() {
        // Send raw string query (old format)
        String query = "GET mykey";
        byte[] payload = query.getBytes(StandardCharsets.UTF_8);

        Message result = router.route(payload, false);

        assertEquals("QUERY_RESPONSE", result.getContent().toStringUtf8());
        verify(mockQueryHandler, times(1)).handle(any(), eq(false));
    }

    @Test
    void testUnknownMessageTypeReturnsError() {
        // Create wrapper with UNSPECIFIED type
        MessageWrapper wrapper = MessageWrapper.newBuilder()
                .setType(MessageType.UNSPECIFIED)
                .setPayload(ByteString.copyFromUtf8("DATA"))
                .build();

        Message result = router.route(wrapper.toByteArray(), false);

        assertTrue(result.getContent().toStringUtf8().contains("ERROR"));
    }
}
