package server.zookeeper.modules;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.ratis.protocol.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.zookeeper.proto.MessageType;
import server.zookeeper.proto.MessageWrapper;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class MessageRouter {
    private static final Logger LOG = LoggerFactory.getLogger(MessageRouter.class);
    private final Map<MessageType, MessageHandler> handlers;
    private final MessageHandler fallbackHandler;

    //! This is Temporary. all messages should be wrapped in protobuf MessageWrapper
    //TODO: Remove RAW_STRING support in future versions
    private enum MessageFormat {
        PROTOBUF_WRAPPED,  // Message wrapped in MessageWrapper protobuf
        RAW_STRING,        // Legacy raw string command (PUT/GET/DELETE)
        UNKNOWN            // Unknown format
    }

    public MessageRouter(MessageHandler fallbackHandler) {
        if (fallbackHandler == null) {
            throw new IllegalArgumentException("Fallback handler cannot be null");
        }

        this.handlers = new HashMap<>();
        this.fallbackHandler = fallbackHandler;

        LOG.info("MessageRouter initialized with fallback handler: {}",
                fallbackHandler.getHandlerType());
    }

    public MessageRouter registerHandler(MessageType messageType, MessageHandler handler) {
        if (messageType == null) {
            throw new IllegalArgumentException("Message type cannot be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("Handler cannot be null");
        }

        handlers.put(messageType, handler);
        LOG.info("Registered handler for message type: {} -> {}",
                messageType, handler.getClass().getSimpleName());

        return this;
    }

    public Message route(byte[] payload, boolean isMutation) {
        try {
            // Try to detect message format
            MessageFormat format = detectMessageFormat(payload);

            LOG.debug("Detected message format: {}", format);

            switch (format) {
                case PROTOBUF_WRAPPED:
                    return routeWrappedMessage(payload, isMutation);

                case RAW_STRING:
                    return routeRawMessage(payload, isMutation);

                default:
                    return createErrorResponse("Unknown message format");
            }

        } catch (Exception e) {
            LOG.error("Error routing message", e);
            return createErrorResponse("Failed to route message: " + e.getMessage());
        }
    }

    private MessageFormat detectMessageFormat(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return MessageFormat.UNKNOWN;
        }

        // Try to parse as protobuf MessageWrapper
        try {
            MessageWrapper.parseFrom(payload);
            return MessageFormat.PROTOBUF_WRAPPED;
        } catch (InvalidProtocolBufferException e) {
            // Additional validation: check if it looks like a text command
            String payloadStr = new String(payload, StandardCharsets.UTF_8);
            if (payloadStr.trim().matches("^(PUT|GET|DELETE)\\s.*")) {
                return MessageFormat.RAW_STRING;
            }
            return MessageFormat.UNKNOWN;
        }
    }

    private Message routeWrappedMessage(byte[] payload, boolean isMutation) {
        try {
            // Parse the MessageWrapper
            MessageWrapper wrapper = MessageWrapper.parseFrom(payload);
            MessageType messageType = wrapper.getType();
            byte[] innerPayload = wrapper.getPayload().toByteArray();

            LOG.debug("Routing wrapped message of type: {}", messageType);

            MessageHandler handler = handlers.get(messageType);

            if (handler == null) {
                LOG.warn("No handler registered for message type: {}", messageType);
                return createErrorResponse("No handler for message type: " + messageType);
            }

            return handler.handle(innerPayload, isMutation);

        } catch (InvalidProtocolBufferException e) {
            LOG.error("Failed to parse MessageWrapper", e);
            return createErrorResponse("Invalid protobuf message: " + e.getMessage());
        }
    }

    private Message routeRawMessage(byte[] payload, boolean isMutation) {
        LOG.debug("Routing raw message to fallback handler");
        return fallbackHandler.handle(payload, isMutation);
    }

    private Message createErrorResponse(String errorMessage) {
        // For backward compatibility, return simple string error
        //TODO: In future, can return ResponseWrapper protobuf
        return Message.valueOf("ERROR: " + errorMessage);
    }

    public Optional<MessageHandler> getHandler(MessageType messageType) {
        return Optional.ofNullable(handlers.get(messageType));
    }

    public boolean hasHandler(MessageType messageType) {
        return handlers.containsKey(messageType);
    }

}
