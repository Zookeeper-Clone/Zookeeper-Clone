package com.example.Modules;

import java.util.HashMap;
import java.util.Map;
import org.apache.ratis.protocol.Message;

public class QueryHandler {
    private final Map<String, String> keyValStore = new HashMap<>();

    public Message handleMutation(String query) {
        String response;

        if (query.startsWith("PUT ")) {
            String payload = query.substring(4);
            String[] parts = payload.split("=", 2);

            if (parts.length != 2) {
                response = "ERROR INVALID MESSAGE";
            } else {
                String key = parts[0];
                String value = parts[1];

                boolean existed = keyValStore.containsKey(key);
                keyValStore.put(key, value);

                response = existed ? "OK ENTRY UPDATED" : "OK ENTRY ADDED";
            }
        } else if (query.startsWith("DELETE ")) {
            String key = query.substring(7).trim();
            response = keyValStore.remove(key) != null ? "OK ENTRY DELETED" : "KEY DOESN'T EXIST";
        } else {
            response = "INVALID QUERY";
        }

        return Message.valueOf(response);
    }

    public Message handleQuery(String query) {
        String response;

        if (query.equals("READALL")) {
            StringBuilder sb = new StringBuilder();
            for (String key : keyValStore.keySet()) {
                sb.append(key).append(" : ").append(keyValStore.get(key)).append("\n");
            }
            response = sb.toString();
        } else if (query.startsWith("GET ")) {
            String key = query.substring(4).trim();
            response = keyValStore.getOrDefault(key, "KEY DOESN'T EXIST");
        } else {
            response = "INVALID QUERY";
        }

        return Message.valueOf(response);
    }
}
