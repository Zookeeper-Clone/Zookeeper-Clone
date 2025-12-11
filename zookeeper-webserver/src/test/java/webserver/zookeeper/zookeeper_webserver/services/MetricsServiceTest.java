package webserver.zookeeper.zookeeper_webserver.services;

import client.zookeeper.ZookeeperClient;
import org.apache.ratis.client.RaftClient;
import org.apache.ratis.protocol.RaftGroupId;
import org.apache.ratis.protocol.RaftPeerId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MetricsServiceTest {

    @Mock
    private ZookeeperClient zookeeperClient;

    @Mock
    private RaftClient raftClient;

    @Mock
    private RaftPeerId leaderId;

    @Mock
    private RaftGroupId groupId;

    private MetricsService metricsService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        metricsService = new MetricsService(zookeeperClient);
        when(zookeeperClient.getRaftClient()).thenReturn(raftClient);
    }

    @Test
    void testCollectMetrics_WithValidLeaderAndGroupId() throws IOException {
        // Arrange
        UUID testUuid = UUID.randomUUID();
        when(raftClient.getLeaderId()).thenReturn(leaderId);
        when(raftClient.getGroupId()).thenReturn(groupId);
        when(leaderId.toString()).thenReturn("leader-1");
        when(groupId.getUuid()).thenReturn(testUuid);

        // Act
        Map<String, Object> metrics = metricsService.collectMetrics();

        // Assert
        assertNotNull(metrics);
        assertEquals("leader-1", metrics.get("leader"));
        assertEquals(testUuid.toString(), metrics.get("groupId"));
        assertTrue(metrics.containsKey("raft"));
        assertTrue(metrics.containsKey("client"));
        assertTrue(metrics.containsKey("peers"));
    }

    @Test
    void testCollectMetrics_WithNullLeader() throws IOException {
        // Arrange
        UUID testUuid = UUID.randomUUID();
        when(raftClient.getLeaderId()).thenReturn(null);
        when(raftClient.getGroupId()).thenReturn(groupId);
        when(groupId.getUuid()).thenReturn(testUuid);

        // Act
        Map<String, Object> metrics = metricsService.collectMetrics();

        // Assert
        assertEquals("unknown", metrics.get("leader"));
        assertEquals(testUuid.toString(), metrics.get("groupId"));
    }

    @Test
    void testCollectMetrics_WithNullGroupId() throws IOException {
        // Arrange
        when(raftClient.getLeaderId()).thenReturn(leaderId);
        when(raftClient.getGroupId()).thenReturn(null);
        when(leaderId.toString()).thenReturn("leader-1");

        // Act
        Map<String, Object> metrics = metricsService.collectMetrics();

        // Assert
        assertEquals("leader-1", metrics.get("leader"));
        assertEquals("unknown", metrics.get("groupId"));
    }

    @Test
    void testCollectMetrics_WithException() {
        // Arrange
        when(raftClient.getLeaderId()).thenThrow(new RuntimeException("Connection error"));

        // Act
        Map<String, Object> metrics = metricsService.collectMetrics();

        // Assert
        assertEquals("unknown", metrics.get("leader"));
        assertEquals("unknown", metrics.get("groupId"));
        assertEquals(List.of(), metrics.get("peers"));
    }

    @Test
    void testCollectMetrics_RaftMetricsStructure() {
        // Act
        Map<String, Object> metrics = metricsService.collectMetrics();
        Map<String, Object> raftMap = (Map<String, Object>) metrics.get("raft");

        // Assert
        assertNotNull(raftMap);
        assertEquals("N/A", raftMap.get("term"));
        assertEquals("N/A", raftMap.get("role"));
        assertEquals("N/A", raftMap.get("log.appliedIndex"));
        assertEquals("N/A", raftMap.get("log.commitIndex"));
        assertEquals("N/A", raftMap.get("log.lastIndex"));
        assertEquals("N/A", raftMap.get("appendEntry.latencyMs"));
        assertEquals("N/A", raftMap.get("heartbeat.latencyMs"));
    }

    @Test
    void testCollectMetrics_ClientMetricsInitialState() {
        // Act
        Map<String, Object> metrics = metricsService.collectMetrics();
        Map<String, Object> clientStats = (Map<String, Object>) metrics.get("client");

        // Assert
        assertNotNull(clientStats);
        assertEquals(0L, clientStats.get("totalRequests"));
        assertEquals(0L, clientStats.get("notLeaderHits"));
        assertEquals(0L, clientStats.get("retryCount"));
    }

    @Test
    void testRecordRequest() {
        // Act
        metricsService.recordRequest();
        metricsService.recordRequest();
        metricsService.recordRequest();

        Map<String, Object> metrics = metricsService.collectMetrics();
        Map<String, Object> clientStats = (Map<String, Object>) metrics.get("client");

        // Assert
        assertEquals(3L, clientStats.get("totalRequests"));
        assertEquals(0L, clientStats.get("notLeaderHits"));
        assertEquals(0L, clientStats.get("retryCount"));
    }

    @Test
    void testRecordNotLeader() {
        // Act
        metricsService.recordNotLeader();
        metricsService.recordNotLeader();

        Map<String, Object> metrics = metricsService.collectMetrics();
        Map<String, Object> clientStats = (Map<String, Object>) metrics.get("client");

        // Assert
        assertEquals(0L, clientStats.get("totalRequests"));
        assertEquals(2L, clientStats.get("notLeaderHits"));
        assertEquals(0L, clientStats.get("retryCount"));
    }

    @Test
    void testRecordRetry() {
        // Act
        metricsService.recordRetry();
        metricsService.recordRetry();
        metricsService.recordRetry();
        metricsService.recordRetry();

        Map<String, Object> metrics = metricsService.collectMetrics();
        Map<String, Object> clientStats = (Map<String, Object>) metrics.get("client");

        // Assert
        assertEquals(0L, clientStats.get("totalRequests"));
        assertEquals(0L, clientStats.get("notLeaderHits"));
        assertEquals(4L, clientStats.get("retryCount"));
    }

    @Test
    void testAllRecordMethods() {
        // Act
        metricsService.recordRequest();
        metricsService.recordRequest();
        metricsService.recordNotLeader();
        metricsService.recordRetry();
        metricsService.recordRetry();
        metricsService.recordRetry();

        Map<String, Object> metrics = metricsService.collectMetrics();
        Map<String, Object> clientStats = (Map<String, Object>) metrics.get("client");

        // Assert
        assertEquals(2L, clientStats.get("totalRequests"));
        assertEquals(1L, clientStats.get("notLeaderHits"));
        assertEquals(3L, clientStats.get("retryCount"));
    }

    @Test
    void testCollectMetrics_PeersAlwaysEmptyList() throws IOException {
        // Arrange
        when(raftClient.getLeaderId()).thenReturn(leaderId);
        when(leaderId.toString()).thenReturn("leader-1");

        // Act
        Map<String, Object> metrics = metricsService.collectMetrics();

        // Assert
        assertEquals(List.of(), metrics.get("peers"));
    }

    @Test
    void testCollectMetrics_MultipleCallsShowUpdatedCounters() {
        // Act - First collection
        Map<String, Object> metrics1 = metricsService.collectMetrics();
        Map<String, Object> clientStats1 = (Map<String, Object>) metrics1.get("client");
        assertEquals(0L, clientStats1.get("totalRequests"));

        // Record some events
        metricsService.recordRequest();
        metricsService.recordRequest();

        // Act - Second collection
        Map<String, Object> metrics2 = metricsService.collectMetrics();
        Map<String, Object> clientStats2 = (Map<String, Object>) metrics2.get("client");

        // Assert
        assertEquals(2L, clientStats2.get("totalRequests"));
    }
}