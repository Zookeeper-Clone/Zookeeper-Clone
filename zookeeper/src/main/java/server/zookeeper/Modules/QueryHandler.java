package server.zookeeper.Modules;

import java.util.HashMap;
import java.util.Map;
import org.apache.ratis.protocol.Message;

public class QueryHandler {
    private final Map<String, String> keyValStore = new HashMap<>();

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
        switch (command.type) {
            case "READALL":
                response = readAll();
                break;
            case "GET":
                response = get(command.payload);
                break;
            default:
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
        boolean existed = keyValStore.containsKey(key);
        keyValStore.put(key, value);
        return existed ? "OK ENTRY UPDATED" : "OK ENTRY ADDED";
    }

    private String delete(String key) {
        return keyValStore.remove(key) != null ? "OK ENTRY DELETED" : "KEY DOESN'T EXIST";
    }

    private String readAll() {
        StringBuilder sb = new StringBuilder();
        for (String key : keyValStore.keySet()) {
            sb.append(key).append(" : ").append(keyValStore.get(key)).append("\n");
        }
        return sb.toString();
    }

    private String get(String key) {
        return keyValStore.getOrDefault(key, "KEY DOESN'T EXIST");
    }
}
