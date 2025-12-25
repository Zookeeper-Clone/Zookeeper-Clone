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
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
} from "@mui/material";

export default function EditPage() {
  const [keyInput, setKeyInput] = useState("");
  const [directoryInput, setDirectoryInput] = useState("");
  const [valueInput, setValueInput] = useState("");
  const [isEphemeral, setIsEphemeral] = useState(false);
  const [operationType, setOperationType] = useState("create");

  // Error dialog state
  const [errorDialogOpen, setErrorDialogOpen] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");


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

  // Load user data from localStorage
  const getCurrentUserData = () => {
    const userDataStr = localStorage.getItem("currentUser");
    console.log("Loading currentUser from localStorage:", userDataStr);

    if (!userDataStr) return { admin: false, permissions: {} };

    try {
      const userData = JSON.parse(userDataStr);
      return {
        admin: userData.admin || false,
        permissions: userData.permissions || {}
      };
    } catch (e) {
      console.error("Error parsing currentUser:", e);
      return { admin: false, permissions: {} };
    }
  };

// Initialize state from localStorage immediately
  const [isAdmin, setIsAdmin] = useState(() => {
    const userData = getCurrentUserData();
    return userData.admin;
  });

  const [userPermissions, setUserPermissions] = useState(() => {
    const userData = getCurrentUserData();
    return userData.permissions;
  });

  useEffect(() => {
    console.log("=== useEffect running to load user data ===");
    const userDataStr = localStorage.getItem("currentUser");
    console.log("currentUser from localStorage in useEffect:", userDataStr);

    if (!userDataStr) {
      setIsAdmin(false);
      setUserPermissions({});
      return;
    }

    try {
      const userData = JSON.parse(userDataStr);
      console.log("Parsed userData:", userData);
      console.log("Setting isAdmin to:", !!userData.admin);
      console.log("Setting userPermissions to:", userData.permissions || {});
      setIsAdmin(!!userData.admin);
      setUserPermissions(userData.permissions || {});
    } catch (e) {
      console.error("Error parsing currentUser:", e);
      setIsAdmin(false);
      setUserPermissions({});
    }
  }, []);

  useEffect(() => {
    const handler = () => {
      const data = JSON.parse(localStorage.getItem("currentUser"));
      if (data) {
        setIsAdmin(!!data.admin);
        setUserPermissions(data.permissions || {});
      }
    };

    window.addEventListener("storage", handler);
    return () => window.removeEventListener("storage", handler);
  }, []);

  // Check if user has specific permission across any directory
  const hasPermission = (permissionType) => {
    const permissionValues = {
      create: 1,
      read: 2,
      update: 4,
      delete: 8,
    };

    const permValue = permissionValues[permissionType];
    if (!permValue) return false;

    // Check if user is admin
    if (isAdmin === true) {
      console.log("User is admin, granting all permissions");
      return true;
    }

    // If no permissions, return false
    if (!userPermissions || Object.keys(userPermissions).length === 0) {
      console.log(`No permissions found for ${permissionType}`);
      return false;
    }

    // Check across all directories
    const hasPermissionResult = Object.values(userPermissions).some(
      (dirPermission) => {
        const numPermission = Number(dirPermission);
        const hasPerm = (numPermission & permValue) === permValue;
        console.log(`Checking directory permission ${dirPermission} for ${permissionType} (${permValue}):`, hasPerm);
        return hasPerm;
      }
    );

    console.log(`Final result for ${permissionType}:`, hasPermissionResult);
    return hasPermissionResult;
  };

  const canCreate = hasPermission("create");
  const canUpdate = hasPermission("update");
  const canDelete = hasPermission("delete");

  console.log("Permission summary - canCreate:", canCreate, "canUpdate:", canUpdate, "canDelete:", canDelete);

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
  const [newOpOperationType, setNewOpOperationType] = useState("create");

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
      operationType: newOpOperationType,
    };

    setCannedOperations([...cannedOperations, newOp]);

    setNewOpKey("");
    setNewOpDir("");
    setNewOpValue("");
    setNewOpType("write");
    setNewOpIsEphemeral(false);
    setNewOpOperationType("create");
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
    isEphemeral,
    operationType = "create"
  ) => {
    const timestamp = new Date().toISOString();
    const payload = { key, directory, value };

    if (commandType === "write") {
      payload.isEphemeral = isEphemeral;
      payload.isUpdate = operationType === "update";
    }

    const endpoint =
      commandType === "write"
        ? "http://localhost:8080/query/write"
        : "http://localhost:8080/query/delete";

    try {
      const response = await fetch(endpoint, {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(payload),
        credentials: "include",
      });

      if (!response.ok) {
        let errorMsg = `Request failed with status ${response.status}`;

        try {
          const responseText = await response.text();

          if (responseText) {
            try {
              const errorData = JSON.parse(responseText);
              errorMsg = errorData.message || errorData.error || responseText;
            } catch {
              errorMsg = responseText;
            }
          }
        } catch (error) {
          console.error("Error reading response:", error);
        }

        setErrorMessage(errorMsg);
        setErrorDialogOpen(true);

        pushHistory({
          key,
          directory: directory || "-",
          value: value || "-",
          type: commandType,
          timestamp: formatTimestamp(timestamp),
          status: "Failed",
          isEphemeral: commandType === "write" ? isEphemeral : undefined,
        });
      } else {
        pushHistory({
          key,
          directory: directory || "-",
          value: value || "-",
          type: commandType,
          timestamp: formatTimestamp(timestamp),
          status: "Success",
          isEphemeral: commandType === "write" ? isEphemeral : undefined,
        });
      }

      updateFrequency(key);
    } catch (error) {
      setErrorMessage(`Network error: ${error.message}`);
      setErrorDialogOpen(true);

      pushHistory({
        key,
        directory: directory || "-",
        value: value || "-",
        type: commandType,
        timestamp: formatTimestamp(timestamp),
        status: "Failed",
        isEphemeral: commandType === "write" ? isEphemeral : undefined,
      });
    }
  };

  const handleWrite = () => {
    if (operationType === "create" && !canCreate) {
      setErrorMessage("You don't have create permission");
      setErrorDialogOpen(true);
      return;
    }
    if (operationType === "update" && !canUpdate) {
      setErrorMessage("You don't have update permission");
      setErrorDialogOpen(true);
      return;
    }

    sendCommand(
      "write",
      keyInput,
      directoryInput,
      valueInput,
      isEphemeral,
      operationType
    );
  };

  const handleDelete = () => {
    if (!canDelete) {
      setErrorMessage("You don't have delete permission");
      setErrorDialogOpen(true);
      return;
    }

    sendCommand("delete", keyInput, directoryInput, null);
  };

  const handleExecute = (op) => {
    if (op.type === "write") {
      const opType = op.operationType || "create";
      if (opType === "create" && !canCreate) {
        setErrorMessage("You don't have create permission");
        setErrorDialogOpen(true);
        return;
      }
      if (opType === "update" && !canUpdate) {
        setErrorMessage("You don't have update permission");
        setErrorDialogOpen(true);
        return;
      }
    } else if (op.type === "delete" && !canDelete) {
      setErrorMessage("You don't have delete permission");
      setErrorDialogOpen(true);
      return;
    }

    sendCommand(
      op.type,
      op.key,
      op.directory,
      op.value,
      op.isEphemeral,
      op.operationType || "create"
    );
  };

  const isWriteDisabled =
    (operationType === "create" && !canCreate) ||
    (operationType === "update" && !canUpdate);

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

            <FormControl fullWidth sx={{ mb: 2 }}>
              <InputLabel id="operation-type-label">Operation Type</InputLabel>
              <Select
                labelId="operation-type-label"
                value={operationType}
                label="Operation Type"
                onChange={(e) => setOperationType(e.target.value)}
                sx={{ textAlign: "left" }}
              >
                <MenuItem value="create" disabled={!canCreate}>
                  Create {!canCreate && "(No Permission)"}
                </MenuItem>
                <MenuItem value="update" disabled={!canUpdate}>
                  Update {!canUpdate && "(No Permission)"}
                </MenuItem>
              </Select>
            </FormControl>

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
                  disabled={isWriteDisabled}
                >
                  Write
                </Button>

                <Button
                  variant="contained"
                  color="error"
                  onClick={handleDelete}
                  disabled={!canDelete}
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

              {newOpType === "write" && (
                <FormControl fullWidth sx={{ mb: 2 }}>
                  <InputLabel id="op-operation-type-label">
                    Operation Type
                  </InputLabel>
                  <Select
                    labelId="op-operation-type-label"
                    value={newOpOperationType}
                    label="Operation Type"
                    onChange={(e) => setNewOpOperationType(e.target.value)}
                    sx={{ textAlign: "left" }}
                  >
                    <MenuItem value="create">Create</MenuItem>
                    <MenuItem value="update">Update</MenuItem>
                  </Select>
                </FormControl>
              )}

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
                  <TableCell>Create/Update</TableCell>
                  <TableCell>Ephemeral</TableCell>
                  <TableCell>Action</TableCell>
                </TableRow>
              </TableHead>

              <TableBody>
                {cannedOperations.map((op, index) => {
                  let canExecute = true;
                  if (op.type === "write") {
                    const opType = op.operationType || "create";
                    canExecute =
                      opType === "create" ? canCreate : canUpdate;
                  } else if (op.type === "delete") {
                    canExecute = canDelete;
                  }

                  return (
                    <TableRow key={op.id}>
                      <TableCell>{index + 1}</TableCell>
                      <TableCell>{op.key}</TableCell>
                      <TableCell>{op.directory}</TableCell>
                      <TableCell>{op.value}</TableCell>
                      <TableCell>{op.type}</TableCell>
                      <TableCell>
                        {op.type === "write"
                          ? op.operationType || "create"
                          : "-"}
                      </TableCell>
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
                          disabled={!canExecute}
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
                  );
                })}
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

      {/* Error Dialog */}
      <Dialog open={errorDialogOpen} onClose={() => setErrorDialogOpen(false)}>
        <DialogTitle>Error</DialogTitle>
        <DialogContent>
          <DialogContentText>{errorMessage}</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setErrorDialogOpen(false)} color="primary">
            Close
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}