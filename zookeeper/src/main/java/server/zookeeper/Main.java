package server.zookeeper;

import java.util.Arrays;
import java.util.UUID;

import org.apache.ratis.conf.Parameters;
import org.apache.ratis.conf.RaftProperties;
import org.apache.ratis.grpc.GrpcFactory;
import org.apache.ratis.protocol.RaftGroup;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.protocol.RaftPeer;

import server.zookeeper.Modules.RatisServer;

public class Main {
    public static void main(String[] args) {
        try {
            String nodeId = System.getenv("NODE_ID");
            String portStr = System.getenv("PORT");
            String cluster = System.getenv("CLUSTER");

            if (nodeId == null || portStr == null || cluster == null) {
                throw new IllegalStateException("NODE_ID, PORT, and CLUSTER must be set");
            }

            int port = Integer.parseInt(portStr);

            // Cluster ID
            RaftGroupId groupId = RaftGroupId.valueOf(
                    UUID.fromString("00000000-0000-0000-0000-000000000001"));

            // Parse cluster peers
            RaftPeer[] peers = Arrays.stream(cluster.split(","))
                    .map(entry -> {
                        // Format: n1:host:6001
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

            // Build Ratis Server
            RatisServer server = new RatisServer(nodeId, port, raftGroup);
            server.start();

        } catch (Exception e) {
            System.err.println("Error starting server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
