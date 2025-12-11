package webserver.zookeeper.zookeeper_webserver.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import webserver.zookeeper.zookeeper_webserver.services.MetricsService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@ExtendWith(MockitoExtension.class)
class MetricsControllerTest {

    @Mock
    private MetricsService metricsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MetricsController metricsController = new MetricsController(metricsService);
        mockMvc = MockMvcBuilders.standaloneSetup(metricsController).build();
    }

    @Test
    void testGetMetrics_ReturnsMetrics() throws Exception {
        // Arrange
        Map<String, Object> mockMetrics = createMockMetrics();
        when(metricsService.collectMetrics()).thenReturn(mockMetrics);

        // Act & Assert
        mockMvc.perform(get("/metrics/ratis"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.leader").value("leader-1"))
                .andExpect(jsonPath("$.groupId").value("test-group-123"))
                .andExpect(jsonPath("$.peers").isArray())
                .andExpect(jsonPath("$.raft").exists())
                .andExpect(jsonPath("$.client").exists());

        verify(metricsService, times(1)).collectMetrics();
    }

    @Test
    void testGetMetrics_ReturnsRaftMetrics() throws Exception {
        // Arrange
        Map<String, Object> mockMetrics = createMockMetrics();
        when(metricsService.collectMetrics()).thenReturn(mockMetrics);

        // Act & Assert
        mockMvc.perform(get("/metrics/ratis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.raft.term").value("N/A"))
                .andExpect(jsonPath("$.raft.role").value("N/A"))
                .andExpect(jsonPath("$.raft['log.appliedIndex']").value("N/A"))
                .andExpect(jsonPath("$.raft['log.commitIndex']").value("N/A"))
                .andExpect(jsonPath("$.raft['log.lastIndex']").value("N/A"))
                .andExpect(jsonPath("$.raft['appendEntry.latencyMs']").value("N/A"))
                .andExpect(jsonPath("$.raft['heartbeat.latencyMs']").value("N/A"));

        verify(metricsService, times(1)).collectMetrics();
    }

    @Test
    void testGetMetrics_ReturnsClientMetrics() throws Exception {
        // Arrange
        Map<String, Object> mockMetrics = createMockMetrics();
        when(metricsService.collectMetrics()).thenReturn(mockMetrics);

        // Act & Assert
        mockMvc.perform(get("/metrics/ratis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client.totalRequests").value(10))
                .andExpect(jsonPath("$.client.notLeaderHits").value(2))
                .andExpect(jsonPath("$.client.retryCount").value(5));

        verify(metricsService, times(1)).collectMetrics();
    }

    @Test
    void testGetMetrics_WithUnknownLeader() throws Exception {
        // Arrange
        Map<String, Object> mockMetrics = createMockMetricsWithUnknownLeader();
        when(metricsService.collectMetrics()).thenReturn(mockMetrics);

        // Act & Assert
        mockMvc.perform(get("/metrics/ratis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leader").value("unknown"))
                .andExpect(jsonPath("$.groupId").value("unknown"));

        verify(metricsService, times(1)).collectMetrics();
    }

    @Test
    void testGetMetrics_WithEmptyClientMetrics() throws Exception {
        // Arrange
        Map<String, Object> mockMetrics = createMockMetricsWithZeroClientStats();
        when(metricsService.collectMetrics()).thenReturn(mockMetrics);

        // Act & Assert
        mockMvc.perform(get("/metrics/ratis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client.totalRequests").value(0))
                .andExpect(jsonPath("$.client.notLeaderHits").value(0))
                .andExpect(jsonPath("$.client.retryCount").value(0));

        verify(metricsService, times(1)).collectMetrics();
    }

    @Test
    void testGetMetrics_VerifiesServiceInvocation() throws Exception {
        // Arrange
        Map<String, Object> mockMetrics = createMockMetrics();
        when(metricsService.collectMetrics()).thenReturn(mockMetrics);

        // Act
        mockMvc.perform(get("/metrics/ratis"));

        // Assert
        verify(metricsService, times(1)).collectMetrics();
        verifyNoMoreInteractions(metricsService);
    }

    @Test
    void testGetMetrics_EmptyPeersList() throws Exception {
        // Arrange
        Map<String, Object> mockMetrics = createMockMetrics();
        when(metricsService.collectMetrics()).thenReturn(mockMetrics);

        // Act & Assert
        mockMvc.perform(get("/metrics/ratis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.peers").isArray())
                .andExpect(jsonPath("$.peers", hasSize(0)));

        verify(metricsService, times(1)).collectMetrics();
    }

    @Test
    void testGetMetrics_ReturnsCorrectContentType() throws Exception {
        // Arrange
        Map<String, Object> mockMetrics = createMockMetrics();
        when(metricsService.collectMetrics()).thenReturn(mockMetrics);

        // Act & Assert
        mockMvc.perform(get("/metrics/ratis"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        verify(metricsService, times(1)).collectMetrics();
    }

    // Helper methods to create mock data

    private Map<String, Object> createMockMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("leader", "leader-1");
        metrics.put("groupId", "test-group-123");
        metrics.put("peers", List.of());

        Map<String, Object> raftMap = new HashMap<>();
        raftMap.put("term", "N/A");
        raftMap.put("role", "N/A");
        raftMap.put("log.appliedIndex", "N/A");
        raftMap.put("log.commitIndex", "N/A");
        raftMap.put("log.lastIndex", "N/A");
        raftMap.put("appendEntry.latencyMs", "N/A");
        raftMap.put("heartbeat.latencyMs", "N/A");
        metrics.put("raft", raftMap);

        Map<String, Object> clientStats = new HashMap<>();
        clientStats.put("totalRequests", 10L);
        clientStats.put("notLeaderHits", 2L);
        clientStats.put("retryCount", 5L);
        metrics.put("client", clientStats);

        return metrics;
    }

    private Map<String, Object> createMockMetricsWithUnknownLeader() {
        Map<String, Object> metrics = createMockMetrics();
        metrics.put("leader", "unknown");
        metrics.put("groupId", "unknown");
        return metrics;
    }

    private Map<String, Object> createMockMetricsWithZeroClientStats() {
        Map<String, Object> metrics = createMockMetrics();

        Map<String, Object> clientStats = new HashMap<>();
        clientStats.put("totalRequests", 0L);
        clientStats.put("notLeaderHits", 0L);
        clientStats.put("retryCount", 0L);
        metrics.put("client", clientStats);

        return metrics;
    }
}