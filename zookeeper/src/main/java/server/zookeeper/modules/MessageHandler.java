package server.zookeeper.modules;

import org.apache.ratis.protocol.Message;

import java.util.concurrent.CompletableFuture;

public interface MessageHandler {

    /**
     * @param payload the serialized message payload (protobuf or raw bytes)
     * @param isMutation true if this is a mutation operation, false for read-only query
     * @return the response message
     * @throws RuntimeException if the message cannot be handled
     */
    CompletableFuture<Message> handle(byte[] payload, boolean isMutation);

    /**
     * Gets the message type this handler supports.
     * Used for handler registration in the router.
     *
     * @return the message type string (e.g., "QUERY", "AUTH")
     */
    String getHandlerType();

    /**
     * Validates if this handler can process the given payload.
     *
     * @param payload the message payload
     * @return true if this handler can process the payload
     */
    default boolean canHandle(byte[] payload) {
        return payload != null && payload.length > 0;
    }
}
