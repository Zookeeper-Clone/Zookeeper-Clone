import React, { useEffect, useState } from "react";

export default function RatisMetricsDashboard() {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    fetch("http://localhost:8080/metrics/ratis")
      .then((r) => {
        if (!r.ok) throw new Error('Failed to fetch');
        return r.json();
      })
      .then((json) => setData(json))
      .catch((err) => {
        console.error('Fetch error:', err);
//        // Mock data for demo purposes
//        setData({
//          leader: "node-1",
//          groupId: "group-0",
//          peers: ["node-1", "node-2", "node-3"],
//          raft: {
//            term: 5,
//            role: "LEADER",
//            "log.appliedIndex": 1234,
//            "log.commitIndex": 1234,
//            "log.lastIndex": 1235,
//            "appendEntry.latencyMs": 12.5,
//            "heartbeat.latencyMs": 3.2
//          },
//          client: {
//            totalRequests: 5678,
//            notLeaderHits: 23,
//            retryCount: 12
//          }
//        });
      });
  }, []);

  if (!data) {
    return (
      <div className="flex justify-center items-center p-24">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500"></div>
      </div>
    );
  }

  return (
    <div className="p-6 bg-gray-50 min-h-screen">
      <h1 className="text-3xl font-bold mb-6 text-gray-800">Ratis Metrics Dashboard</h1>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-6">
        {/* Cluster Overview */}
        <div className="bg-white rounded-lg shadow p-6">
          <h2 className="text-xl font-semibold mb-4 text-gray-700">Cluster Overview</h2>
          <div className="space-y-2">
            <p className="text-gray-600"><span className="font-medium">Leader:</span> {data.leader}</p>
            <p className="text-gray-600"><span className="font-medium">Term:</span> {data.raft.term}</p>
            <p className="text-gray-600"><span className="font-medium">Role:</span> {data.raft.role}</p>
            <p className="text-gray-600"><span className="font-medium">Group ID:</span> {data.groupId}</p>
            <p className="text-gray-600"><span className="font-medium">Peers:</span> {data.peers.join(", ")}</p>
          </div>
        </div>

        {/* Raft Health */}
        <div className="bg-white rounded-lg shadow p-6">
          <h2 className="text-xl font-semibold mb-4 text-gray-700">Raft Health</h2>
          <div className="space-y-2">
            <p className="text-gray-600"><span className="font-medium">Applied Index:</span> {data.raft["log.appliedIndex"]}</p>
            <p className="text-gray-600"><span className="font-medium">Commit Index:</span> {data.raft["log.commitIndex"]}</p>
            <p className="text-gray-600"><span className="font-medium">Last Index:</span> {data.raft["log.lastIndex"]}</p>
            <div className="border-t pt-3 mt-3">
              <p className="text-gray-600"><span className="font-medium">AppendEntry Latency:</span> {data.raft["appendEntry.latencyMs"]} ms</p>
              <p className="text-gray-600"><span className="font-medium">Heartbeat Latency:</span> {data.raft["heartbeat.latencyMs"]} ms</p>
            </div>
          </div>
        </div>
      </div>

      {/* Client Behaviour */}
      <div className="bg-white rounded-lg shadow p-6 mb-6">
        <h2 className="text-xl font-semibold mb-4 text-gray-700">Client Behaviour</h2>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
          <div className="bg-blue-50 p-4 rounded">
            <p className="text-sm text-gray-600">Total Requests</p>
            <p className="text-2xl font-bold text-blue-600">{data.client.totalRequests}</p>
          </div>
          <div className="bg-yellow-50 p-4 rounded">
            <p className="text-sm text-gray-600">NotLeader Hits</p>
            <p className="text-2xl font-bold text-yellow-600">{data.client.notLeaderHits}</p>
          </div>
          <div className="bg-purple-50 p-4 rounded">
            <p className="text-sm text-gray-600">Retry Count</p>
            <p className="text-2xl font-bold text-purple-600">{data.client.retryCount}</p>
          </div>
        </div>

        {/* Latency Chart */}
        <div className="mt-6">
          <h3 className="text-lg font-medium mb-3 text-gray-700">Latency Metrics</h3>
          <div className="h-64 flex items-end justify-around bg-gray-50 rounded p-4">
            <div className="flex flex-col items-center">
              <div
                className="w-24 bg-blue-500 rounded-t"
                style={{ height: `${Math.min(data.raft['appendEntry.latencyMs'] * 10, 200)}px` }}
              ></div>
              <p className="mt-2 text-sm font-medium">AppendEntry</p>
              <p className="text-xs text-gray-600">{data.raft['appendEntry.latencyMs']} ms</p>
            </div>
            <div className="flex flex-col items-center">
              <div
                className="w-24 bg-green-500 rounded-t"
                style={{ height: `${Math.min(data.raft['heartbeat.latencyMs'] * 10, 200)}px` }}
              ></div>
              <p className="mt-2 text-sm font-medium">Heartbeat</p>
              <p className="text-xs text-gray-600">{data.raft['heartbeat.latencyMs']} ms</p>
            </div>
          </div>
        </div>
      </div>

      {/* Log Index Chart */}
      <div className="bg-white rounded-lg shadow p-6">
        <h2 className="text-xl font-semibold mb-4 text-gray-700">Log Index Status</h2>
        <div className="h-64 flex items-end justify-around bg-gray-50 rounded p-4">
          <div className="flex flex-col items-center">
            <div
              className="w-24 bg-indigo-500 rounded-t"
              style={{ height: `${(data.raft['log.commitIndex'] / data.raft['log.lastIndex']) * 200}px` }}
            ></div>
            <p className="mt-2 text-sm font-medium">Commit</p>
            <p className="text-xs text-gray-600">{data.raft['log.commitIndex']}</p>
          </div>
          <div className="flex flex-col items-center">
            <div
              className="w-24 bg-teal-500 rounded-t"
              style={{ height: `${(data.raft['log.appliedIndex'] / data.raft['log.lastIndex']) * 200}px` }}
            ></div>
            <p className="mt-2 text-sm font-medium">Applied</p>
            <p className="text-xs text-gray-600">{data.raft['log.appliedIndex']}</p>
          </div>
          <div className="flex flex-col items-center">
            <div
              className="w-24 bg-pink-500 rounded-t"
              style={{ height: '200px' }}
            ></div>
            <p className="mt-2 text-sm font-medium">Last</p>
            <p className="text-xs text-gray-600">{data.raft['log.lastIndex']}</p>
          </div>
        </div>
      </div>
    </div>
  );
}