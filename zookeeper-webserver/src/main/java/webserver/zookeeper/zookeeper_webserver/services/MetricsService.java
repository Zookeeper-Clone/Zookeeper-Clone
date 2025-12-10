package webserver.zookeeper.zookeeper_webserver.services;

import client.zookeeper.ZookeeperClient;
import org.apache.ratis.client.RaftClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MetricsService {

    private final ZookeeperClient zookeeperClient;

    // simple counters for client-side statistics
    private long totalRequests = 0;
    private long notLeaderHits = 0;
    private long retryCount = 0;

    @Autowired
    public MetricsService(ZookeeperClient zookeeperClient) {
        this.zookeeperClient = zookeeperClient;
    }

    public Map<String, Object> collectMetrics() {
        Map<String, Object> response = new HashMap<>();

        RaftClient raftClient = zookeeperClient.getRaftClient();

        // --- Raft metrics (placeholder - actual Ratis metrics require server-side access) ---
        Map<String, Object> raftMap = new HashMap<>();
        raftMap.put("term", "N/A");
        raftMap.put("role", "N/A");
        raftMap.put("log.appliedIndex", "N/A");
        raftMap.put("log.commitIndex", "N/A");
        raftMap.put("log.lastIndex", "N/A");
        raftMap.put("appendEntry.latencyMs", "N/A");
        raftMap.put("heartbeat.latencyMs", "N/A");

        response.put("raft", raftMap);

        // --- Cluster Info ---
        try {
            // Get leader ID from RaftClient
            var leaderId = raftClient.getLeaderId();
            response.put("leader", leaderId != null ? leaderId.toString() : "unknown");

            // Get group ID from RaftClient
            var groupId = raftClient.getGroupId();
            response.put("groupId", groupId != null ? groupId.getUuid().toString() : "unknown");

            // Peers information is not directly accessible from client API
            // Would need to be configured/stored separately or obtained from server
            response.put("peers", List.of());

        } catch (Exception e) {
            response.put("leader", "unknown");
            response.put("peers", List.of());
            response.put("groupId", "unknown");
        }

        // --- Client metrics ---
        Map<String, Object> clientStats = new HashMap<>();
        clientStats.put("totalRequests", totalRequests);
        clientStats.put("notLeaderHits", notLeaderHits);
        clientStats.put("retryCount", retryCount);

        response.put("client", clientStats);

        return response;
    }

    // Called manually by code that performs client operations:
    public void recordRequest() {
        totalRequests++;
    }

    public void recordNotLeader() {
        notLeaderHits++;
    }

    public void recordRetry() {
        retryCount++;
    }
}