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

    // Client-side statistics
    private long totalRequests = 0;
    private long notLeaderHits = 0;
    private long retryCount = 0;
    private long successCount = 0;
    private long failCount = 0;
    private long totalLatencyMs = 0;

    @Autowired
    public MetricsService(ZookeeperClient zookeeperClient) {
        this.zookeeperClient = zookeeperClient;
    }

    /**
     * Collect metrics for dashboard
     */
    public Map<String, Object> collectMetrics() {
        Map<String, Object> response = new HashMap<>();
        RaftClient raftClient = zookeeperClient.getRaftClient();

        // --- Raft metrics (placeholders) ---
        Map<String, Object> raftMap = new HashMap<>();
        raftMap.put("term", "N/A");
        raftMap.put("role", "N/A");
        raftMap.put("log.appliedIndex", "N/A");
        raftMap.put("log.commitIndex", "N/A");
        raftMap.put("log.lastIndex", "N/A");
        raftMap.put("appendEntry.latencyMs", "N/A");
        raftMap.put("heartbeat.latencyMs", "N/A");
        raftMap.put("success", successCount);
        raftMap.put("failure", failCount);
        response.put("raft", raftMap);

        // --- Cluster Info ---
        try {
            var leaderId = raftClient.getLeaderId();
            response.put("leader", leaderId != null ? leaderId.toString() : "unknown");

            var groupId = raftClient.getGroupId();
            response.put("groupId", groupId != null ? groupId.getUuid().toString() : "unknown");

            // Peers are no longer retrieved from RaftGroup
            response.put("peers", List.of());

        } catch (Exception e) {
            response.put("leader", "unknown");
            response.put("groupId", "unknown");
            response.put("peers", List.of());
        }

        // --- Client metrics ---
        Map<String, Object> clientStats = new HashMap<>();
        clientStats.put("totalRequests", totalRequests);
        clientStats.put("notLeaderHits", notLeaderHits);
        clientStats.put("retryCount", retryCount);
        clientStats.put("successCount", successCount);
        clientStats.put("failCount", failCount);
        clientStats.put("avgLatencyMs", totalRequests > 0 ? totalLatencyMs / totalRequests : "N/A");

        response.put("client", clientStats);

        return response;
    }

    // --- Methods to update metrics ---
    public void recordRequest() { totalRequests++; }
    public void recordNotLeader() { notLeaderHits++; }
    public void recordRetry() { retryCount++; }
    public void recordSuccess() { successCount++; }
    public void recordFail() { failCount++; }
    public void recordLatency(long latencyMs) { totalLatencyMs += latencyMs; }
}