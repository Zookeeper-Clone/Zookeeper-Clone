package server.zookeeper.Modules;

import java.nio.charset.StandardCharsets;
import org.apache.ratis.protocol.Message;
import server.zookeeper.DB.DataBase;

public class QueryHandler {
    private final DataBase keyValStore;
    private final String INVALID_QUERY = "INVALID QUERY";

    public QueryHandler(DataBase keyValStore) {
        this.keyValStore = keyValStore;
    }

    private enum CommandType {
        PUT,
        DELETE,
        GET,
        INVALID
    }

    private static class Command {
        CommandType type;
        String payload;
        String directoryName;

        Command(CommandType type, String payload, String directoryName) {
            this.type = type;
            this.payload = payload;
            this.directoryName = directoryName;
        }
    }

    public Message handleMutation(String query) {
        Command command = parseCommand(query);
        return executeMutation(command);
    }

    public Message handleQuery(String query) {
        Command command = parseCommand(query);
        return executeQuery(command);
    }

    private Command parseCommand(String query) {
        String trimmed = query.trim();

        // Extract type
        int spaceIndex = trimmed.indexOf(' ');
        String typeString;
        String rest;

        if (spaceIndex == -1) {
            typeString = trimmed.toUpperCase();
            rest = "";
        } else {
            typeString = trimmed.substring(0, spaceIndex).toUpperCase();
            rest = trimmed.substring(spaceIndex + 1).trim();
        }

        CommandType type;
        try {
            type = CommandType.valueOf(typeString);
        } catch (IllegalArgumentException e) {
            type = CommandType.INVALID;
        }

        String directoryName = null;
        String payload = rest;

        int inIndex = rest.lastIndexOf(" IN ");
        if (inIndex != -1) {
            payload = rest.substring(0, inIndex).trim();
            directoryName = rest.substring(inIndex + 4).trim();
        }

        return new Command(type, payload, directoryName);
    }

    private Message executeMutation(Command cmd) {
        String response;
        switch (cmd.type) {
            case PUT:
                response = put(cmd.payload, cmd.directoryName);
                break;
            case DELETE:
                response = delete(cmd.payload, cmd.directoryName);
                break;
            default:
                response = INVALID_QUERY;
        }
        return Message.valueOf(response);
    }

    private Message executeQuery(Command cmd) {
        String response;
        switch (cmd.type) {
            case GET:
                response = get(cmd.payload, cmd.directoryName);
                break;
            default:
                response = INVALID_QUERY;
        }
        return Message.valueOf(response);
    }

    private String put(String payload, String directoryName) {
        String[] parts = payload.split("=", 2);
        if (parts.length != 2)
            return "ERROR INVALID MESSAGE";

        String key = parts[0];
        String value = parts[1];

        if (directoryName == null)
            keyValStore.put(key.getBytes(), value.getBytes());
        else
            keyValStore.put(key.getBytes(), value.getBytes(), directoryName);

        return "OK ENTRY ADDED";
    }

    private String delete(String key, String directoryName) {
        if (directoryName == null)
            keyValStore.delete(key.getBytes());
        else
            keyValStore.delete(key.getBytes(), directoryName);

        return "OK";
    }

    private String get(String key, String directoryName) {
        byte[] val;

        if (directoryName == null)
            val = keyValStore.get(key.getBytes());
        else
            val = keyValStore.get(key.getBytes(), directoryName);

        if (val == null)
            return "__NOT_FOUND__";

        return new String(val, StandardCharsets.UTF_8);
    }
}
