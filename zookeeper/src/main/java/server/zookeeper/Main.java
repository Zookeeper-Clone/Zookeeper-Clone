package server.zookeeper;

import java.util.Arrays;
import java.util.UUID;

import org.apache.ratis.protocol.RaftGroup;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.protocol.RaftPeer;

import server.zookeeper.Modules.RatisServer;

public class Main {

    public static void main(String[] args) {
        try {
            // Use the helper method to get required env variables
            String nodeId = getRequiredEnv("NODE_ID");
            String portStr = getRequiredEnv("PORT");
            String cluster = getRequiredEnv("CLUSTER");
            String threshold = getRequiredEnv("SNAPSHOT_THRESHOLD");
            int port = Integer.parseInt(portStr);
            int autoTriggerThreshold = Integer.parseInt(threshold);
            RaftGroupId groupId = RaftGroupId.valueOf(
                    UUID.fromString("00000000-0000-0000-0000-000000000001"));

            RaftPeer[] peers = Arrays.stream(cluster.split(","))
                    .map(entry -> {
                        String[] parts = entry.split(":");
                        String peerId = parts[0];
                        String host = parts[1];
                        int peerPort = Integer.parseInt(parts[2]);

                        return RaftPeer.newBuilder()
                                .setId(peerId)
                                .setAddress(host + ":" + peerPort)
                                .build();
                    })
                    .toArray(RaftPeer[]::new);

            RaftGroup raftGroup = RaftGroup.valueOf(groupId, peers);

            RatisServer server = new RatisServer(nodeId, port, raftGroup, autoTriggerThreshold);
            server.start();

        } catch (IllegalStateException e) {
            System.err.println("Environment variable error: " + e.getMessage());
            System.exit(1);
        } catch (NumberFormatException e) {
            System.err.println("PORT and SNAPSHOT_THRESHOLD must be a valid integers");
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Error starting server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static String getRequiredEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException(key + " must be set");
        }
        return value;
    }
}
