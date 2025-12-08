package server.zookeeper.modules;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.ratis.protocol.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.zookeeper.proto.MessageType;
import server.zookeeper.proto.MessageWrapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class MessageRouter {
    private static final Logger LOG = LoggerFactory.getLogger(MessageRouter.class);
    private final Map<MessageType, MessageHandler> handlers;
    private final MessageHandler fallbackHandler;

    public MessageRouter(MessageHandler fallbackHandler) {
        if (fallbackHandler == null) {
            throw new IllegalArgumentException("Fallback handler cannot be null");
        }

        this.handlers = new HashMap<>();
        this.fallbackHandler = fallbackHandler;

        LOG.info("MessageRouter initialized with fallback handler: {}",
                fallbackHandler.getHandlerType());
    }

    public void registerHandler(MessageType messageType, MessageHandler handler) {
        if (messageType == null) {
            throw new IllegalArgumentException("Message type cannot be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("Handler cannot be null");
        }

        handlers.put(messageType, handler);
        LOG.info("Registered handler for message type: {} -> {}",
                messageType, handler.getClass().getSimpleName());

    }

    public Message route(byte[] payload, boolean isMutation) {
        try {
            // Try to detect message format
            MessageType messageType = detectMessageType(payload);

            LOG.debug("Detected message format: {}", messageType);

            switch (messageType) {
                case AUTH:
                    LOG.info("MESSAGE IS AUTH MESSAGE");
                case QUERY:
                    LOG.info("MESSAGE IS QUERY MESSAGE");
                    return routeMessage(payload, isMutation);
                default:
                    return createErrorResponse("Unknown message type");
            }

        } catch (Exception e) {
            LOG.error("Error routing message", e);
            return createErrorResponse("Failed to route message: " + e.getMessage());
        }
    }

    private MessageType detectMessageType(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return MessageType.UNSPECIFIED;
        }

        try {
            MessageWrapper wrapper = MessageWrapper.parseFrom(payload);
            return wrapper.getType();
        } catch (InvalidProtocolBufferException e) {
            return MessageType.UNSPECIFIED;
        }
    }

    private Message routeMessage(byte[] payload, boolean isMutation) {
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
            LOG.info("routeMessage - routing to {}" , handler.getHandlerType());
            return handler.handle(innerPayload, isMutation);

        } catch (InvalidProtocolBufferException e) {
            LOG.error("Failed to parse MessageWrapper", e);
            return createErrorResponse("Invalid protobuf message: " + e.getMessage());
        }
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
