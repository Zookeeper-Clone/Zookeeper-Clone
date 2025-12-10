import React, { useState, useEffect } from "react";
import {
  Box, Grid, TextField, Button, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, Paper, Typography
} from "@mui/material";

export default function ReadPage() {
  const [keyInput, setKeyInput] = useState("");
  const [directoryInput, setDirectoryInput] = useState("");


  // Load from localStorage
  const [historyData, setHistoryData] = useState(
    JSON.parse(localStorage.getItem("historyData")) || []
  );
  const [frequencyData, setFrequencyData] = useState(
    JSON.parse(localStorage.getItem("frequencyData")) || {}
  );
  const [cannedOperations, setCannedOperations] = useState(
    JSON.parse(localStorage.getItem("cannedOperations")) || []
  );
  const clearHistory = () => {
    setHistoryData([]);
    localStorage.removeItem("historyData");
  };

  const deleteCannedOperation = (id) => {
    const updated = cannedOperations.filter(op => op.id !== id);
    setCannedOperations(updated);
  };

  const clearFrequency = () => {
    setFrequencyData({});
    localStorage.removeItem("frequencyData");
  };

  // --- Save to localStorage whenever state changes ---
  useEffect(() => {
    localStorage.setItem("historyData", JSON.stringify(historyData));
  }, [historyData]);

  useEffect(() => {
    localStorage.setItem("frequencyData", JSON.stringify(frequencyData));
  }, [frequencyData]);

  useEffect(() => {
    localStorage.setItem("cannedOperations", JSON.stringify(cannedOperations));
  }, [cannedOperations]);

  const formatTimestamp = (isoString) => {
    const date = new Date(isoString);

    const day = String(date.getDate()).padStart(2, "0");
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const year = date.getFullYear();

    let hours = date.getHours();
    const minutes = String(date.getMinutes()).padStart(2, "0");

    const ampm = hours >= 12 ? "PM" : "AM";
    hours = hours % 12;
    hours = hours ? hours : 12; // 0 → 12

    return `${day}-${month}-${year} ${hours}:${minutes} ${ampm}`;
  };


  const [showAddOp, setShowAddOp] = useState(false);
  const [newOpKey, setNewOpKey] = useState("");
  const [newOpDir, setNewOpDir] = useState("");

  // -------------------------------------------------------
  // Utility: Update History + Frequency
  // -------------------------------------------------------
  const pushHistory = (entry) => {
    const updated = [entry, ...historyData].slice(0, 5); // Keep last 5
    setHistoryData(updated);
  };

  const updateFrequency = (key) => {
    const now = new Date().toISOString();

    const updated = {
      ...frequencyData,
      [key]: {
        frequency: (frequencyData[key]?.frequency || 0) + 1,
        latestTimestamp: formatTimestamp(now),
      },
    };

    setFrequencyData(updated);
  };

  const addCannedOperation = () => {
    if (!newOpKey.trim()) return;

    const nextId =
      cannedOperations.length === 0
        ? 1
        : Math.max(...cannedOperations.map(op => op.id)) + 1;

    const newOp = {
      id: nextId,
      key: newOpKey,
      directory: newOpDir
    };

    setCannedOperations([...cannedOperations, newOp]);

    setNewOpKey("");
    setNewOpDir("");
    setShowAddOp(false);
  };
  const frequencyTable = Object.entries(frequencyData)
    .map(([key, v]) => ({ key, ...v }))
    .sort((a, b) => b.frequency - a.frequency)
    .slice(0, 5);


  const handleRead = async () => {
    const payload = { key: keyInput, directory: directoryInput };

    const response = await fetch("http://localhost:8080/query/read", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(payload),
      credentials: "include"
    });

    const timestamp = new Date().toISOString();
    const result = response.ok ? await response.text() : null;

    pushHistory({
      key: keyInput,
      directory: (directoryInput !== "") ? directoryInput : "-",
      value: result,
      timestamp: formatTimestamp(timestamp),
      status: response.ok ? "Success" : "Failed"
    });

    updateFrequency(keyInput);
  };

  const handleExecute = async (opKey, opDirectory) => {
    const payload = { key: opKey, directory: opDirectory };

    const response = await fetch("http://localhost:8080/query/read", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(payload)
    });

    const timestamp = new Date().toISOString();
    const result = response.ok ? await response.text() : null;

    pushHistory({
      key: opKey,
      directory: (opDirectory !== "") ? opDirectory : "-",
      value: result,
      timestamp : formatTimestamp(timestamp),
      status: response.ok ? "Success" : "Failed"
    });

    updateFrequency(opKey);
  };

  return (
    <Box sx={{ p: 3 }}>
      <Grid container spacing={4}>
        {/* Left Column */}
        <Grid item xs={12} md={6} sx={{width : "50%"}}>
          <Box sx={{ mb: 3 }}>
            <TextField
              label="Key"
              value={keyInput}
              onChange={(e) => setKeyInput(e.target.value)}
              fullWidth
              sx={{ mb: 2 }}
            />

            <TextField
              label="Directory"
              value={directoryInput}
              onChange={(e) => setDirectoryInput(e.target.value)}
              fullWidth
              sx={{ mb: 2 }}
            />

            <Button variant="contained" color="primary" onClick={handleRead}>
              Read
            </Button>
          </Box>

          <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 1 }}>
            <Typography variant="h6">Canned Operations</Typography>
            <Button variant="outlined" onClick={() => setShowAddOp(true)}>
              + Add Operation
            </Button>
          </Box>

          {showAddOp && (
            <Box sx={{ mb: 2, p: 2, border: "1px solid #ccc", borderRadius: 2 }}>
              <Typography variant="subtitle1" sx={{ mb: 1 }}>New Operation</Typography>

              <TextField
                label="Key"
                value={newOpKey}
                onChange={(e) => setNewOpKey(e.target.value)}
                fullWidth
                sx={{ mb: 2 }}
              />

              <TextField
                label="Directory"
                value={newOpDir}
                onChange={(e) => setNewOpDir(e.target.value)}
                fullWidth
                sx={{ mb: 2 }}
              />

              <Button variant="contained" onClick={addCannedOperation}>
                Save
              </Button>

              <Button
                variant="text"
                sx={{ ml: 2 }}
                onClick={() => setShowAddOp(false)}
              >
                Cancel
              </Button>
            </Box>
          )}

          <TableContainer component={Paper}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>#</TableCell>
                  <TableCell>Key</TableCell>
                  <TableCell>Directory</TableCell>
                  <TableCell>Action</TableCell>
                </TableRow>
              </TableHead>

              <TableBody>
                {cannedOperations.map((op, index) => (
                  <TableRow key={op.id}>
                    <TableCell>{index + 1}</TableCell>
                    <TableCell>{op.key}</TableCell>
                    <TableCell>{op.directory}</TableCell>
                    <TableCell>
                      <Button
                        variant="outlined"
                        sx={{ mr: 1 }}
                        onClick={() => handleExecute(op.key, op.directory)}
                      >
                        Execute
                      </Button>

                      <Button
                        variant="contained"
                        color="error"
                        onClick={() => deleteCannedOperation(op.id)}
                      >
                        Delete
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>

            </Table>
          </TableContainer>
        </Grid>

        {/* Right Column */}
        <Grid item xs={12} md={6} sx={{width : "45%"}}>
          <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" , mb: 1}}>
            <Typography variant="h6">History (last 5)</Typography>
            <Button variant="outlined" color="error" onClick={clearHistory}>
              Clear
            </Button>
          </Box>
          <TableContainer component={Paper} sx={{ mb: 3 }}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Key</TableCell>
                  <TableCell>Value</TableCell>
                  <TableCell>Directory</TableCell>
                  <TableCell>Timestamp</TableCell>
                  <TableCell>Status</TableCell>
                </TableRow>
              </TableHead>

              <TableBody>
                {historyData.map((row, index) => (
                  <TableRow key={index}>
                    <TableCell>{row.key}</TableCell>
                    <TableCell>{row.value}</TableCell>
                    <TableCell>{row.directory}</TableCell>
                    <TableCell>{row.timestamp}</TableCell>
                    <TableCell>{row.status}</TableCell>
                  </TableRow>
                ))}
              </TableBody>

            </Table>
          </TableContainer>
          <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 1}}>
            <Typography variant="h6">Frequency Table (last 5)</Typography>
            <Button variant="outlined" color="error" onClick={clearFrequency}>
              Clear
            </Button>
          </Box>

          <TableContainer component={Paper}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Key</TableCell>
                  <TableCell>Frequency</TableCell>
                  <TableCell>Latest Timestamp</TableCell>
                </TableRow>
              </TableHead>

              <TableBody>
                {frequencyTable.map((row, index) => (
                  <TableRow key={index}>
                    <TableCell>{row.key}</TableCell>
                    <TableCell>{row.frequency}</TableCell>
                    <TableCell>{row.latestTimestamp}</TableCell>
                  </TableRow>
                ))}
              </TableBody>

            </Table>
          </TableContainer>
        </Grid>
      </Grid>
    </Box>
  );
}
