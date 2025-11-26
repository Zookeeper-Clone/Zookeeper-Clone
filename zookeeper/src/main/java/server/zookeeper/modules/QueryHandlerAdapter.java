package server.zookeeper.modules;

import org.apache.ratis.protocol.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public class QueryHandlerAdapter implements MessageHandler {

    private static final Logger LOG = LoggerFactory.getLogger(QueryHandlerAdapter.class);
    private static final String HANDLER_TYPE = "QUERY";

    private final QueryHandler queryHandler;

    public QueryHandlerAdapter(QueryHandler queryHandler) {
        if (queryHandler == null) {
            throw new IllegalArgumentException("QueryHandler cannot be null");
        }
        this.queryHandler = queryHandler;
    }

    @Override
    public Message handle(byte[] payload, boolean isMutation) {
        try {
            String query = new String(payload, StandardCharsets.UTF_8);

            LOG.debug("Handling {} query: {}", isMutation ? "mutation" : "read-only", query);

            if (isMutation) {
                return queryHandler.handleMutation(query);
            } else {
                return queryHandler.handleQuery(query);
            }

        } catch (Exception e) {
            LOG.error("Error handling query", e);
            throw new RuntimeException("Failed to handle query: " + e.getMessage(), e);
        }
    }

    @Override
    public String getHandlerType() {
        return HANDLER_TYPE;
    }

    @Override
    public boolean canHandle(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return false;
        }

        // Check if payload starts with a valid command
        String query = new String(payload, StandardCharsets.UTF_8);
        return query.trim().matches("^(PUT|GET|DELETE)\\s.*");
    }
}
