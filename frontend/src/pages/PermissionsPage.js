import React, { useState } from "react";
import {
  Box,
  TextField,
  Button,
  Typography,
  RadioGroup,
  FormControlLabel,
  Radio,
  Select,
  MenuItem,
  OutlinedInput,
  Chip,
  FormControl,
  InputLabel,
  Divider,
  Alert,
} from "@mui/material";

const PERMISSION_OPTIONS = ["create", "read", "update", "delete"];

export default function UserPermissionsPage() {
  const [email, setEmail] = useState("");
  const [action, setAction] = useState(""); // admin | createDir | changeDir

  const [directory, setDirectory] = useState("");
  const [permissions, setPermissions] = useState([]);

  const [message, setMessage] = useState(null);
  const [error, setError] = useState(null);

  const resetMessages = () => {
    setMessage(null);
    setError(null);
  };

  const validateEmail = () =>
    email && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);

  /* ---------------- EXECUTE ACTION ---------------- */

  const applyPermission = async () => {
    resetMessages();

    if (!validateEmail()) {
      setError("Invalid email.");
      return;
    }

    try {
      let response;

      /** ✅ EXACT controller mappings with proper if-else **/

      if (action === "admin") {
        response = await fetch(
          `http://localhost:8080/users/${encodeURIComponent(email)}/set-is-admin`,
          {
            method: "PUT",
            credentials: "include",
          }
        );
      } else if (action === "createDir") {
        response = await fetch(
          `http://localhost:8080/users/${encodeURIComponent(email)}/set-can-create-directory`,
          {
            method: "PUT",
            credentials: "include",
          }
        );
      } else if (action === "changeDir") {
        if (!directory.trim()) {
          setError("Directory name is required.");
          return;
        }
        if (permissions.length === 0) {
          setError("Select at least one permission.");
          return;
        }

        response = await fetch(
          `http://localhost:8080/users/${encodeURIComponent(email)}/permissions`,
          {
            method: "PUT",
            credentials: "include",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              directory,
              permissions,
            }),
          }
        );
      } else {
        setError("Please select an action.");
        return;
      }

      if (!response || !response.ok) {
        const errorText = await response.text().catch(() => "Unknown error");
        throw new Error(errorText);
      }

      setMessage("Permission applied successfully!");
      // Only reset directory fields for changeDir action
      if (action === "changeDir") {
        setDirectory("");
        setPermissions([]);
      }
    } catch (err) {
      console.error("Permission update error:", err);
      setError(`Permission update failed: ${err.message || "Unknown error"}`);
    }
  };

  /* ---------------- UI ---------------- */

  return (
    <Box maxWidth={1400} mx="auto" mt={4} p={3}>
      <Typography variant="h5" gutterBottom>
        User Permissions Management
      </Typography>

      {message && <Alert severity="success" sx={{ mb: 2 }}>{message}</Alert>}
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}

      <TextField
        fullWidth
        label="User Email"
        margin="normal"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
      />

      <Divider sx={{ my: 3 }} />

      <RadioGroup
        value={action}
        onChange={(e) => setAction(e.target.value)}
      >
        <FormControlLabel
          value="admin"
          control={<Radio />}
          label="Set User as Admin"
        />
        <FormControlLabel
          value="createDir"
          control={<Radio />}
          label="Allow Create Directories"
        />
        <FormControlLabel
          value="changeDir"
          control={<Radio />}
          label="Change Directory Permissions"
        />
      </RadioGroup>

      {action === "changeDir" && (
        <Box mt={2}>
          <TextField
            fullWidth
            label="Directory Name"
            margin="normal"
            value={directory}
            onChange={(e) => setDirectory(e.target.value)}
          />

          <FormControl fullWidth margin="normal">
            <InputLabel>Permissions</InputLabel>
            <Select
              multiple
              value={permissions}
              onChange={(e) => setPermissions(e.target.value)}
              input={<OutlinedInput label="Permissions" />}
              renderValue={(selected) => (
                <Box sx={{ display: "flex", gap: 0.5, flexWrap: "wrap" }}>
                  {selected.map((p) => (
                    <Chip key={p} label={p.toUpperCase()} />
                  ))}
                </Box>
              )}
            >
              {PERMISSION_OPTIONS.map((perm) => (
                <MenuItem key={perm} value={perm}>
                  {perm.toUpperCase()}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
        </Box>
      )}

      <Button
        variant="contained"
        fullWidth
        sx={{ mt: 3 }}
        disabled={!action || !email}
        onClick={applyPermission}
      >
        Apply Permission
      </Button>
    </Box>
  );
}