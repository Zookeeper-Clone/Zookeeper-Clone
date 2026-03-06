import React, { useEffect, useState } from "react";
import {
  Box,
  Card,
  CardContent,
  Grid,
  Typography,
  CircularProgress,
  LinearProgress,
  Stack,
} from "@mui/material";

export default function RatisMetricsDashboard() {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    fetch("http://localhost:8080/metrics/ratis")
      .then((r) => {
        if (!r.ok) throw new Error("Failed to fetch");
        return r.json();
      })
      .then((json) => setData(json))
      .catch((err) => {
        console.error("Fetch error:", err);
        setError(err.message);
      });
  }, []);

  if (error) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" p={6}>
        <Typography color="error">{error}</Typography>
      </Box>
    );
  }

  if (!data) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" p={6}>
        <CircularProgress />
      </Box>
    );
  }

  const safeNumber = (val) => (val === "N/A" ? 0 : Number(val));

  return (
    <Box p={4} minHeight="100vh">
      <Typography variant="h4" fontWeight="bold" gutterBottom>
        Ratis Metrics Dashboard
      </Typography>

      {/* Cluster Overview & Raft Health */}
      <Grid container spacing={3} mb={3}>
        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Cluster Overview
              </Typography>
              <Stack spacing={1}>
                <Typography>
                  <strong>Leader:</strong> {data.leader}
                </Typography>
                <Typography>
                  <strong>Group ID:</strong> {data.groupId}
                </Typography>
                <Typography>
                  <strong>Peers:</strong>{" "}
                  {data.peers.length > 0 ? data.peers.join(", ") : "None"}
                </Typography>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={6}>
          <Card>
            <CardContent>
              <Typography variant="h6" gutterBottom>
                Raft Health
              </Typography>
              <Stack spacing={1}>
                <Typography>
                  <strong>AppendEntry Latency:</strong>{" "}
                  {data.raft["appendEntry.latencyMs"]} ms
                </Typography>
                <Typography>
                  <strong>Election Count:</strong> {data.raft.electionCount}
                </Typography>
                <Typography>
                  <strong>Timeout Count:</strong> {data.raft.timeoutCount}
                </Typography>
                <Typography>
                  <strong>Pending Requests:</strong>{" "}
                  {data.raft.numPendingRequestsInQueue}
                </Typography>
                <Box mt={2}>
                  <Typography>
                    <strong>Success:</strong> {data.raft.success}
                  </Typography>
                  <Typography>
                    <strong>Failure:</strong> {data.raft.failure}
                  </Typography>
                </Box>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* Client Behaviour */}
      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            Client Behaviour
          </Typography>
          <Grid container spacing={2} mb={3}>
            {[
              {
                label: "Total Requests",
                value: data.client.totalRequests,
                color: "primary.main",
              },
              {
                label: "NotLeader Hits",
                value: data.client.notLeaderHits,
                color: "warning.main",
              },
              {
                label: "Retry Count",
                value: data.client.retryCount,
                color: "purple",
              },
              {
                label: "Success Count",
                value: data.client.successCount,
                color: "success.main",
              },
              {
                label: "Fail Count",
                value: data.client.failCount,
                color: "error.main",
              },
            ].map((item) => (
              <Grid item xs={12} md={2.4} key={item.label}>
                <Card sx={{ textAlign: "center" }}>
                  <CardContent>
                    <Typography>{item.label}</Typography>
                    <Typography
                      variant="h5"
                      fontWeight="bold"
                      color={item.color}
                    >
                      {item.value}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
            ))}
          </Grid>

          {/* Success / Failure Ratio Bar */}
          <Box mt={2}>
            <Typography variant="subtitle1" gutterBottom>
              Success / Failure Ratio
            </Typography>
            <Box
              sx={{
                display: "flex",
                height: 24,
                borderRadius: 2,
                overflow: "hidden",
                backgroundColor: "#eee",
              }}
            >
              <Box
                sx={{
                  width: `${
                    data.client.successCount + data.client.failCount > 0
                      ? (data.client.successCount /
                          (data.client.successCount + data.client.failCount)) *
                        100
                      : 50
                  }%`,
                  backgroundColor: "success.main",
                }}
              />
              <Box
                sx={{
                  width: `${
                    data.client.successCount + data.client.failCount > 0
                      ? (data.client.failCount /
                          (data.client.successCount + data.client.failCount)) *
                        100
                      : 50
                  }%`,
                  backgroundColor: "error.main",
                }}
              />
            </Box>
            <Stack direction="row" justifyContent="space-between" mt={1}>
              <Typography variant="caption" color="success.main">
                {data.client.successCount} Success
              </Typography>
              <Typography variant="caption" color="error.main">
                {data.client.failCount} Fail
              </Typography>
            </Stack>
          </Box>

          {/* Average Latency */}
          <Box mt={4}>
            <Typography variant="subtitle1" gutterBottom>
              Average Latency
            </Typography>
            <LinearProgress
              variant="determinate"
              value={Math.min(safeNumber(data.client.avgLatencyMs) * 2, 100)}
              sx={{ height: 12, borderRadius: 6 }}
            />
            <Typography variant="caption">
              {data.client.avgLatencyMs} ms
            </Typography>
          </Box>
        </CardContent>
      </Card>

      {/* Zookeeper Server Metrics */}
      {data.zookeeper && (
        <Card>
          <CardContent>
            <Typography variant="h6" gutterBottom>
              Zookeeper Server Metrics
            </Typography>
            <Grid container spacing={2}>
              <Grid item xs={12} md={4}>
                <Card sx={{ textAlign: "center" }}>
                  <CardContent>
                    <Typography>Client Read Requests</Typography>
                    <Typography
                      variant="h5"
                      fontWeight="bold"
                      color="info.main"
                    >
                      {data.zookeeper.clientReadRequests}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid item xs={12} md={4}>
                <Card sx={{ textAlign: "center" }}>
                  <CardContent>
                    <Typography>Client Write Requests</Typography>
                    <Typography
                      variant="h5"
                      fontWeight="bold"
                      color="success.main"
                    >
                      {data.zookeeper.clientWriteRequests}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
              <Grid item xs={12} md={4}>
                <Card sx={{ textAlign: "center" }}>
                  <CardContent>
                    <Typography>Failed Read Requests</Typography>
                    <Typography
                      variant="h5"
                      fontWeight="bold"
                      color="error.main"
                    >
                      {data.zookeeper.numFailedClientReadOnServer}
                    </Typography>
                  </CardContent>
                </Card>
              </Grid>
            </Grid>
          </CardContent>
        </Card>
      )}
    </Box>
  );
}
