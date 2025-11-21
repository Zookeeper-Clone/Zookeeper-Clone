package server.zookeeper.Modules;

import java.nio.charset.StandardCharsets;
import org.apache.ratis.protocol.Message;
import server.zookeeper.DB.CRocksDB;
import server.zookeeper.DB.DataBase;

public class QueryHandler {
    private final DataBase keyValStore;

    public QueryHandler() {
        this.keyValStore = CRocksDB.getInstance();
    }

    private static class Command {
        String type;
        String payload;

        Command(String type, String payload) {
            this.type = type;
            this.payload = payload;
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
        int spaceIndex = trimmed.indexOf(' ');

        if (spaceIndex == -1) {
            return new Command(trimmed.toUpperCase(), "");
        }

        String type = trimmed.substring(0, spaceIndex).toUpperCase();
        String payload = trimmed.substring(spaceIndex + 1).trim();

        return new Command(type, payload);
    }

    private Message executeMutation(Command command) {
        String response;
        switch (command.type) {
            case "PUT":
                response = put(command.payload);
                break;
            case "DELETE":
                response = delete(command.payload);
                break;
            default:
                response = "INVALID QUERY";
        }
        return Message.valueOf(response);
    }

    private Message executeQuery(Command command) {
        String response;
        if (command.type.equals("GET")) {
            response = get(command.payload);
        } else {
            response = "INVALID QUERY";
        }
        return Message.valueOf(response);
    }

    private String put(String payload) {
        String[] parts = payload.split("=", 2);
        if (parts.length != 2)
            return "ERROR INVALID MESSAGE";

        String key = parts[0];
        String value = parts[1];
        keyValStore.put(key.getBytes(), value.getBytes());
        return "OK ENTRY ADDED";
    }

    private String delete(String key) {
        keyValStore.delete(key.getBytes());
        return "OK"; // no need to know if it exists before or not
    }

    private String get(String key) {
        byte[] val = keyValStore.get(key.getBytes());
        if (val == null) {
            return "__NOT_FOUND__";
        }
        return new String(val, StandardCharsets.UTF_8);
    }
}
