import React, { useState, useEffect } from "react";
import {
  Box,
  Grid,
  TextField,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Typography,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  FormControlLabel,
  Switch,
} from "@mui/material";

export default function EditPage() {
  const [keyInput, setKeyInput] = useState("");
  const [directoryInput, setDirectoryInput] = useState("");
  const [valueInput, setValueInput] = useState("");
  const [isEphemeral, setIsEphemeral] = useState(false);

  // Load from localStorage
  const [historyData, setHistoryData] = useState(
    JSON.parse(localStorage.getItem("writeHistoryData")) || []
  );
  const [frequencyData, setFrequencyData] = useState(
    JSON.parse(localStorage.getItem("writeFrequencyData")) || {}
  );
  const [cannedOperations, setCannedOperations] = useState(
    JSON.parse(localStorage.getItem("writeCannedOperations")) || []
  );

  const clearHistory = () => {
    setHistoryData([]);
    localStorage.removeItem("writeHistoryData");
  };

  const clearFrequency = () => {
    setFrequencyData({});
    localStorage.removeItem("writeFrequencyData");
  };

  const deleteCannedOperation = (id) => {
    const updated = cannedOperations.filter((op) => op.id !== id);
    setCannedOperations(updated);
  };

  // Save to localStorage
  useEffect(() => {
    localStorage.setItem("writeHistoryData", JSON.stringify(historyData));
  }, [historyData]);

  useEffect(() => {
    localStorage.setItem("writeFrequencyData", JSON.stringify(frequencyData));
  }, [frequencyData]);

  useEffect(() => {
    localStorage.setItem(
      "writeCannedOperations",
      JSON.stringify(cannedOperations)
    );
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
    hours = hours ? hours : 12;

    return `${day}-${month}-${year} ${hours}:${minutes} ${ampm}`;
  };

  const pushHistory = (entry) => {
    const updated = [entry, ...historyData].slice(0, 5);
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

  const [showAddOp, setShowAddOp] = useState(false);
  const [newOpKey, setNewOpKey] = useState("");
  const [newOpDir, setNewOpDir] = useState("");
  const [newOpValue, setNewOpValue] = useState("");
  const [newOpType, setNewOpType] = useState("write");
  const [newOpIsEphemeral, setNewOpIsEphemeral] = useState(false);

  const addCannedOperation = () => {
    if (!newOpKey.trim()) return;

    const nextId =
      cannedOperations.length === 0
        ? 1
        : Math.max(...cannedOperations.map((op) => op.id)) + 1;

    const newOp = {
      id: nextId,
      key: newOpKey,
      directory: newOpDir,
      value: newOpValue,
      type: newOpType,
      isEphemeral: newOpIsEphemeral,
    };

    setCannedOperations([...cannedOperations, newOp]);

    setNewOpKey("");
    setNewOpDir("");
    setNewOpValue("");
    setNewOpType("write");
    setNewOpIsEphemeral(false);
    setShowAddOp(false);
  };

  const frequencyTable = Object.entries(frequencyData)
    .map(([key, v]) => ({ key, ...v }))
    .sort((a, b) => b.frequency - a.frequency)
    .slice(0, 5);

  const sendCommand = async (
    commandType,
    key,
    directory,
    value,
    isEphemeral
  ) => {
    const timestamp = new Date().toISOString();
    const payload = { key, directory, value };

    if (commandType === "write") {
      payload.isEphemeral = isEphemeral;
    }

    const endpoint =
      commandType === "write"
        ? "http://localhost:8080/query/write"
        : "http://localhost:8080/query/delete";

    const response = await fetch(endpoint, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(payload),
      credentials: "include",
    });

    pushHistory({
      key,
      directory: directory || "-",
      value: value || "-",
      type: commandType,
      timestamp: formatTimestamp(timestamp),
      status: response.ok ? "Success" : "Failed",
      isEphemeral: commandType === "write" ? isEphemeral : undefined,
    });

    updateFrequency(key);
  };

  const handleWrite = () => {
    sendCommand("write", keyInput, directoryInput, valueInput, isEphemeral);
  };

  const handleDelete = () => {
    sendCommand("delete", keyInput, directoryInput, null);
  };

  const handleExecute = (op) => {
    sendCommand(op.type, op.key, op.directory, op.value, op.isEphemeral);
  };

  return (
    <Box sx={{ p: 3 }}>
      <Grid container spacing={4}>
        {/* Left Column */}
        <Grid item xs={12} md={6} sx={{ width: "50%" }}>
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

            <TextField
              label="Value (for write)"
              value={valueInput}
              onChange={(e) => setValueInput(e.target.value)}
              fullWidth
              sx={{ mb: 2 }}
            />

            <Box
              sx={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
              }}
            >
              <Box>
                <Button
                  variant="contained"
                  sx={{ mr: 2 }}
                  onClick={handleWrite}
                >
                  Write
                </Button>

                <Button
                  variant="contained"
                  color="error"
                  onClick={handleDelete}
                >
                  Delete
                </Button>
              </Box>

              <FormControlLabel
                control={
                  <Switch
                    checked={isEphemeral}
                    onChange={(e) => setIsEphemeral(e.target.checked)}
                  />
                }
                label="Ephemeral"
              />
            </Box>
          </Box>

          {/* Add Operation */}
          <Box sx={{ display: "flex", justifyContent: "space-between", mb: 1 }}>
            <Typography variant="h6">Canned Operations</Typography>
            <Button variant="outlined" onClick={() => setShowAddOp(true)}>
              + Add Operation
            </Button>
          </Box>

          {showAddOp && (
            <Box
              sx={{ mb: 2, p: 2, border: "1px solid #ccc", borderRadius: 2 }}
            >
              <Typography variant="subtitle1" sx={{ mb: 1 }}>
                New Operation
              </Typography>

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

              <TextField
                label="Value (for write)"
                value={newOpValue}
                onChange={(e) => setNewOpValue(e.target.value)}
                fullWidth
                sx={{ mb: 2 }}
              />

              <FormControlLabel
                control={
                  <Switch
                    checked={newOpIsEphemeral}
                    onChange={(e) => setNewOpIsEphemeral(e.target.checked)}
                  />
                }
                label="Ephemeral"
                sx={{ mb: 2, display: "block" }}
              />

              <FormControl fullWidth sx={{ mb: 2 }}>
                <InputLabel id="op-type-label">Type</InputLabel>
                <Select
                  labelId="op-type-label"
                  value={newOpType}
                  label="Type"
                  onChange={(e) => setNewOpType(e.target.value)}
                  sx={{ textAlign: "left" }}
                >
                  <MenuItem value="write">Write</MenuItem>
                  <MenuItem value="delete">Delete</MenuItem>
                </Select>
              </FormControl>

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

          {/* Canned Operations Table */}
          <TableContainer component={Paper}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>#</TableCell>
                  <TableCell>Key</TableCell>
                  <TableCell>Directory</TableCell>
                  <TableCell>Value</TableCell>
                  <TableCell>Type</TableCell>
                  <TableCell>Ephemeral</TableCell>
                  <TableCell>Action</TableCell>
                </TableRow>
              </TableHead>

              <TableBody>
                {cannedOperations.map((op, index) => (
                  <TableRow key={op.id}>
                    <TableCell>{index + 1}</TableCell>
                    <TableCell>{op.key}</TableCell>
                    <TableCell>{op.directory}</TableCell>
                    <TableCell>{op.value}</TableCell>
                    <TableCell>{op.type}</TableCell>
                    <TableCell>
                      {op.type === "write"
                        ? op.isEphemeral
                          ? "Yes"
                          : "No"
                        : "-"}
                    </TableCell>
                    <TableCell>
                      <Button
                        variant="outlined"
                        sx={{ mr: 1 }}
                        onClick={() => handleExecute(op)}
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
        <Grid item xs={12} md={6} sx={{ width: "45%" }}>
          {/* History */}
          <Box sx={{ display: "flex", justifyContent: "space-between", mb: 1 }}>
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
                  <TableCell>Type</TableCell>
                  <TableCell>Ephemeral</TableCell>
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
                    <TableCell>{row.type}</TableCell>
                    <TableCell>
                      {row.type === "write"
                        ? row.isEphemeral
                          ? "Yes"
                          : "No"
                        : "-"}
                    </TableCell>
                    <TableCell>{row.timestamp}</TableCell>
                    <TableCell>{row.status}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>

          {/* Frequency */}
          <Box sx={{ display: "flex", justifyContent: "space-between", mb: 1 }}>
            <Typography variant="h6">Frequency (top 5)</Typography>
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
