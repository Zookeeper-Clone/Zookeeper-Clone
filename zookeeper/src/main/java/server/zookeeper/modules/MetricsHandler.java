package server.zookeeper.modules;

import java.util.concurrent.CompletableFuture;

import org.apache.ratis.metrics.RatisMetricRegistry;
import org.apache.ratis.metrics.impl.RatisMetricRegistryImpl;
import org.apache.ratis.protocol.Message;
import org.apache.ratis.server.RaftServer;
import org.apache.ratis.server.metrics.LeaderElectionMetrics;
import org.apache.ratis.metrics.Timekeeper;
import org.apache.ratis.metrics.impl.DefaultTimekeeperImpl;
import org.apache.ratis.server.metrics.RaftServerMetricsImpl;
import org.apache.ratis.server.metrics.SegmentedRaftLogMetrics;
import org.apache.ratis.thirdparty.com.codahale.metrics.Gauge;
import org.apache.ratis.thirdparty.com.google.common.base.Supplier;
import org.apache.ratis.thirdparty.com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.zookeeper.proto.MessageType;
import server.zookeeper.proto.ResponseWrapper;
import server.zookeeper.proto.metrics.MetricsProto.*;

public class MetricsHandler implements MessageHandler {
    private static final Logger LOG = LoggerFactory.getLogger(MetricsHandler.class);
    private RaftServer raftServer;

    public void setRaftServer(RaftServer raftServer) {
        this.raftServer = raftServer;
        LOG.info("RaftServer instance set for MetricsHandler");
    }

    @Override
    public CompletableFuture<Message> handle(byte[] payload, boolean isMutation) {
        try {
            LOG.debug("Processing metrics request");
            MetricsResponse response = collectMetrics();

            ResponseWrapper wrapper = ResponseWrapper.newBuilder()
                    .setSuccess(true)
                    .setPayload(com.google.protobuf.ByteString.copyFrom(response.toByteArray()))
                    .build();

            return CompletableFuture.completedFuture(Message.valueOf(ByteString.copyFrom(wrapper.toByteArray())));
        } catch (Exception e) {
            LOG.error("Error collecting metrics", e);
            return CompletableFuture.completedFuture(createErrorResponse("Failed to collect metrics: " + e.getMessage()));
        }
    }

    private MetricsResponse collectMetrics() {
        MetricsResponse.Builder response = MetricsResponse.newBuilder();

        if (raftServer == null) {
            return response
                    .setSuccess(false)
                    .setErrorMessage("RaftServer not initialized")
                    .build();
        }

        try {
            RaftServer.Division division = raftServer.getDivision(raftServer.getGroupIds().iterator().next());
            if (division == null) {
                return response
                        .setSuccess(false)
                        .setErrorMessage("No Raft division found")
                        .build();
            }

            collectLeaderElectionMetrics(division, response);
            collectClientRequestMetrics(division, response);
            collectRequestQueueMetrics(division, response);
            collectLatencyMetrics(division, response);

            response.setSuccess(true);
        } catch (Exception e) {
            LOG.error("Error collecting metrics", e);
            response.setSuccess(false);
            response.setErrorMessage("Error collecting metrics: " + e.getMessage());
        }

        return response.build();
    }

    private void collectLeaderElectionMetrics(RaftServer.Division division, MetricsResponse.Builder response) {
        RatisMetricRegistry metricsRegistry = LeaderElectionMetrics.createRegistry(division.getMemberId());

        if (metricsRegistry instanceof RatisMetricRegistryImpl) {
            RatisMetricRegistryImpl impl = (RatisMetricRegistryImpl) metricsRegistry;

            long electionCount = impl.counter(LeaderElectionMetrics.LEADER_ELECTION_COUNT_METRIC).getCount();
            response.setElectionCount(electionCount);

            long timeoutCount = impl.counter(LeaderElectionMetrics.LEADER_ELECTION_TIMEOUT_COUNT_METRIC).getCount();
            response.setTimeoutCount(timeoutCount);
        }
    }

    private void collectClientRequestMetrics(RaftServer.Division division, MetricsResponse.Builder response) {
        RaftServerMetricsImpl serverMetrics = (RaftServerMetricsImpl) division.getRaftServerMetrics();
        RatisMetricRegistry registry = serverMetrics.getRegistry();

        if (registry instanceof RatisMetricRegistryImpl) {
            RatisMetricRegistryImpl impl = (RatisMetricRegistryImpl) registry;

            Timekeeper readTimer = impl.timer(RaftServerMetricsImpl.RAFT_CLIENT_READ_REQUEST);
            response.setClientReadRequests(getTimerCount(readTimer));

            Timekeeper writeTimer = impl.timer(RaftServerMetricsImpl.RAFT_CLIENT_WRITE_REQUEST);
            response.setClientWriteRequests(getTimerCount(writeTimer));

            long failedReads = impl.counter(RaftServerMetricsImpl.RATIS_SERVER_FAILED_CLIENT_READ_COUNT).getCount();
            response.setNumFailedClientReadOnServer(failedReads);
        }
    }

    private void collectRequestQueueMetrics(RaftServer.Division division, MetricsResponse.Builder response) {
        RaftServerMetricsImpl serverMetrics = (RaftServerMetricsImpl) division.getRaftServerMetrics();
        RatisMetricRegistry registry = serverMetrics.getRegistry();

        if (registry instanceof RatisMetricRegistryImpl) {
            RatisMetricRegistryImpl impl = (RatisMetricRegistryImpl) registry;
            try {
                Gauge gauge = (Gauge) impl.get(RaftServerMetricsImpl.REQUEST_QUEUE_SIZE);

                if (gauge != null) {
                    // The gauge wraps a Supplier<Integer>, get the value
                    Object gaugeValue = gauge.getValue();

                    // The gauge returns a Supplier<Integer>, so we need to get from it
                    if (gaugeValue instanceof Supplier) {
                        @SuppressWarnings("unchecked")
                        Supplier<Integer> supplier = (Supplier<Integer>) gaugeValue;
                        int queueSize = supplier.get();
                        response.setNumPendingRequestsInQueue(queueSize);
                    } else if (gaugeValue instanceof Integer) {
                        response.setNumPendingRequestsInQueue((Integer) gaugeValue);
                    }
                } else {
                    response.setNumPendingRequestsInQueue(0);
                }
            } catch (Exception e) {
                LOG.error("Could not retrieve request queue size gauge", e);
                response.setNumPendingRequestsInQueue(0);
            }
        }
    }

    private void collectLatencyMetrics(RaftServer.Division division, MetricsResponse.Builder response) {
        collectAppendEntryLatency(division, response);
    }

    private void collectAppendEntryLatency(RaftServer.Division division, MetricsResponse.Builder response) {
        RatisMetricRegistry logRegistry = SegmentedRaftLogMetrics.createRegistry(division.getMemberId());

        if (logRegistry instanceof RatisMetricRegistryImpl) {
            RatisMetricRegistryImpl impl = (RatisMetricRegistryImpl) logRegistry;
            Timekeeper appendLatency = impl.timer(SegmentedRaftLogMetrics.RAFT_LOG_APPEND_ENTRY_LATENCY);
            double avgLatencyMs = getTimerMean(appendLatency);
            response.setAppendEntryLatency(avgLatencyMs);
        }
    }

    private long getTimerCount(Timekeeper timekeeper) {
        if (timekeeper instanceof DefaultTimekeeperImpl) {
            return ((DefaultTimekeeperImpl) timekeeper).getTimer().getCount();
        }
        return 0;
    }

    private double getTimerMean(Timekeeper timekeeper) {
        if (timekeeper instanceof DefaultTimekeeperImpl) {
            // Convert nanoseconds to milliseconds
            return ((DefaultTimekeeperImpl) timekeeper).getTimer().getSnapshot().getMean() / 1_000_000.0;
        }
        return 0.0;
    }

    private Message createErrorResponse(String errorMessage) {
        ResponseWrapper response = ResponseWrapper.newBuilder()
                .setSuccess(false)
                .setErrorMessage(errorMessage)
                .build();
        return Message.valueOf(String.valueOf(ByteString.copyFrom(response.toByteArray())));
    }

    @Override
    public String getHandlerType() {
        return "Metrics";
    }
}
