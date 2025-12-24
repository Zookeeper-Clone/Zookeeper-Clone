package server.zookeeper.modules;

import java.nio.charset.StandardCharsets;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.zookeeper.DB.DataBase;
import server.zookeeper.proto.query.QueryResponse;
import server.zookeeper.proto.query.QueryType;
import server.zookeeper.proto.query.UserQuery;
import server.zookeeper.util.ReservedDirectories;

public class QueryHandler implements MessageHandler {
    private final DataBase keyValStore;
    private final SessionManager sessionManager;
    private static final String HANDLER_TYPE = "QUERY";
    private final Logger LOG = LoggerFactory.getLogger(QueryHandler.class);

    public QueryHandler(DataBase keyValStore, SessionManager sessionManager) {
        this.keyValStore = keyValStore;
        this.sessionManager = sessionManager;
    }

    @Override
    public Message handle(byte[] payload, boolean isMutation) {
        QueryResponse.Builder response = QueryResponse.newBuilder();
        try {
            UserQuery query = UserQuery.parseFrom(payload);
            String directory = query.getDirectory().isEmpty() ? null : query.getDirectory();

            if (ReservedDirectories.isReserved(directory)) {
                response.setSuccess(false)
                        .setErrorMessage(ReservedDirectories.getReservedDirectoryError(directory));
                return Message.valueOf(ByteString.copyFrom(response.build().toByteArray()));
            }
            switch (query.getQueryType()) {
                case GET:
                    get(directory, query, response);
                    break;
                case WRITE:
                    write(isMutation, response, query, directory);
                    break;
                case DELETE:
                    delete(isMutation, response, query, directory);
                    break;
                default:
                    response.setSuccess(false).setErrorMessage("Invalid query type");
                    break;
            }
        } catch (Exception e) {
            response.setSuccess(false).setErrorMessage("Failed to handle query: " + e.getMessage());
        }
        LOG.info("SUCCESSFULLY EXECUTED");
        return Message.valueOf(ByteString.copyFrom(response.build().toByteArray()));
    }

    private void delete(boolean isMutation, QueryResponse.Builder response, UserQuery query, String directory) {
        if (!isMutation) {
            response.setSuccess(false).setErrorMessage("DELETE operation requires mutation flag");
            return;
        }
        String key = query.getKey();

        String dir = (directory == null || directory.isEmpty()) ? null : directory;
        byte[] existing = getExisting(dir, key);

        if (existing == null) {
            response.setSuccess(false)
                    .setErrorMessage("Key does not exist")
                    .setValue("__NOT_FOUND__");
            LOG.info("KEY DOESN'T EXIST");
        } else {
            // Key exists, perform deletion
            if (dir == null)
                keyValStore.delete(key.getBytes());
            else
                keyValStore.delete(key.getBytes(), dir);

            response.setSuccess(true)
                    .setValue("OK ENTRY DELETED");
        }
    }

    private byte[] getExisting(String dir, String key) {
        return (dir == null) ? keyValStore.get(key.getBytes())
                : keyValStore.get(key.getBytes(), dir);
    }

    private void write(boolean isMutation, QueryResponse.Builder response, UserQuery query, String directory) {
        if (!isMutation) {
            response.setSuccess(false).setErrorMessage("WRITE operation requires mutation flag");
        } else {
            String key = query.getKey();
            String val = query.getValue();

            if (directory == null)
                keyValStore.put(key.getBytes(), val.getBytes());
            else
                keyValStore.put(key.getBytes(), val.getBytes(), directory);

            if (query.getIsEphemeral()) {
                sessionManager.addEphemeralEntry(query.getSessionToken(), key, directory);
            }
            response.setSuccess(true).setValue("OK ENTRY ADDED");
        }
    }

    private void get(String directory, UserQuery query, QueryResponse.Builder response) {
        byte[] valueArr = getExisting(directory, query.getKey());
        String value = (valueArr == null) ? "__NOT_FOUND__" : new String(valueArr, StandardCharsets.UTF_8);
        if (value.equals("__NOT_FOUND__")) {
            response.setSuccess(false).setErrorMessage("key not found").setValue(value);
        } else {
            response.setSuccess(true).setValue(value);
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

        try {
            UserQuery request = UserQuery.parseFrom(payload);
            return request.getQueryType() != QueryType.QUERY_TYPE_UNSPECIFIED;
        } catch (InvalidProtocolBufferException e) {
            LOG.debug("Payload cannot be parsed as UserQuery", e);
            return false;
        }
    }
}
