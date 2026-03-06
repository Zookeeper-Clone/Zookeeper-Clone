import React, { useState, useEffect } from 'react';
import {
  Box,
  Card,
  CardContent,
  Typography,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Paper,
  Checkbox,
  IconButton,
  Button,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Chip,
  Grid,
  Collapse,
  Alert
} from '@mui/material';
import {
  Delete as DeleteIcon,
  Edit as EditIcon,
  ExpandMore as ExpandMoreIcon,
  ExpandLess as ExpandLessIcon,
  Add as AddIcon,
  Check as CheckIcon,
  Close as CloseIcon
} from '@mui/icons-material';

export default function PermissionsPage() {
  /* ===================== STATE ===================== */

  const [users, setUsers] = useState([]);
  const [requests, setRequests] = useState([]);

  const [selectedUsers, setSelectedUsers] = useState([]);
  const [filterPermission, setFilterPermission] = useState('all');
  const [addDialogOpen, setAddDialogOpen] = useState(false);
  const [editDialogOpen, setEditDialogOpen] = useState(false);
  const [requestsExpanded, setRequestsExpanded] = useState(false);
  const [selectedCard, setSelectedCard] = useState(null);
  const [alertMessage, setAlertMessage] = useState(null);

  const [newUser, setNewUser] = useState({
    email: '',
    password: '',
    confirmPassword: '',
    permission: 'read'
  });

  const [passwordError, setPasswordError] = useState('');

  const [editingUser, setEditingUser] = useState(null);

  /* ===================== LOAD DATA ===================== */

  useEffect(() => {
    fetch('/api/users')
      .then(res => res.json())
      .then(data => setUsers(data))
      .catch(err => console.error(err));

    fetch('/api/permission-requests')
      .then(res => res.json())
      .then(data => setRequests(data))
      .catch(() => setRequests([]));
  }, []);

  /* ===================== DERIVED ===================== */

  const readOnlyCount = users.filter(u => u.permission === 'read').length;
  const editCount = users.filter(u => u.permission === 'edit').length;
  const requestsCount = requests.length;

  const filteredUsers = selectedCard
    ? users.filter(u => u.permission === selectedCard)
    : filterPermission === 'all'
      ? users
      : users.filter(u => u.permission === filterPermission);

  /* ===================== HELPERS ===================== */

  const showAlert = (message, severity) => {
    setAlertMessage({ message, severity });
    setTimeout(() => setAlertMessage(null), 3000);
  };

  const validatePasswords = () => {
    if (newUser.password !== newUser.confirmPassword) {
      setPasswordError('Passwords do not match.');
      return false;
    }
    if (newUser.password.length < 8) {
      setPasswordError('Password must be at least 8 characters.');
      return false;
    }
    setPasswordError('');
    return true;
  };

  /* ===================== SELECTION ===================== */

  const handleSelectAll = (e) => {
    setSelectedUsers(
      e.target.checked ? filteredUsers.map(u => u.id) : []
    );
  };

  const handleSelectUser = (id) => {
    setSelectedUsers(prev =>
      prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]
    );
  };

  /* ===================== CRUD ===================== */

  const handleDeleteUsers = async () => {
    await Promise.all(
      selectedUsers.map(id => {
        const user = users.find(u => u.id === id);
        return fetch(`/api/users/${user.email}`, { method: 'DELETE' });
      })
    );

    setUsers(prev => prev.filter(u => !selectedUsers.includes(u.id)));
    setSelectedUsers([]);
    showAlert('User(s) deleted successfully', 'success');
  };

  const handleAddUser = async () => {
    setPasswordError('');

    if (!validatePasswords()) return;

    try {
      const response = await fetch('http://localhost:8080/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'include',
        body: JSON.stringify({
          email: newUser.email,
          password: newUser.password
        })
      });

      if (!response.ok) {
        const raw = await response.text();
        showAlert(raw || 'Failed to register user', 'error');
        return;
      }

      // OPTIONAL: set permission after register
      if (newUser.permission === 'edit') {
        await fetch(`/api/users/${newUser.email}/permission`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ isAdmin: true })
        });
      }

      setUsers(prev => [
        ...prev,
        {
          id: Date.now(),
          email: newUser.email,
          permission: newUser.permission
        }
      ]);

      setAddDialogOpen(false);
      setNewUser({
        email: '',
        password: '',
        confirmPassword: '',
        permission: 'read'
      });

      showAlert('User registered successfully', 'success');
    } catch (err) {
      console.error(err);
      showAlert('Network error. Please try again.', 'error');
    }
  };

  const handleEditPermission = (user) => {
    setEditingUser(user);
    setEditDialogOpen(true);
  };

  const handleSavePermission = async () => {
    await fetch(`/api/users/${editingUser.email}/permission`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        permission: editingUser.permission,
        isAdmin: editingUser.permission === 'edit'
      })
    });

    setUsers(prev =>
      prev.map(u =>
        u.email === editingUser.email
          ? { ...u, permission: editingUser.permission }
          : u
      )
    );

    setEditDialogOpen(false);
    setEditingUser(null);
    showAlert('Permission updated successfully', 'success');
  };

  const handleBulkChangePermissions = (permission) => {
    setUsers(prev =>
      prev.map(u =>
        selectedUsers.includes(u.id) ? { ...u, permission } : u
      )
    );
    setSelectedUsers([]);
    showAlert('Permissions updated successfully', 'success');
  };

  /* ===================== REQUESTS ===================== */

  const handleApproveRequest = async (request) => {
    await fetch(`/api/permission-requests/${request.id}/approve`, {
      method: 'POST'
    });

    setUsers(prev => [...prev, {
      id: Date.now(),
      email: request.email,
      permission: request.requestedPermission
    }]);

    setRequests(prev => prev.filter(r => r.id !== request.id));
    showAlert(`Request approved for ${request.email}`, 'success');
  };

  const handleDenyRequest = async (request) => {
    await fetch(`/api/permission-requests/${request.id}/deny`, {
      method: 'POST'
    });

    setRequests(prev => prev.filter(r => r.id !== request.id));
    showAlert(`Request denied for ${request.email}`, 'info');
  };

  /* ===================== UI ===================== */

  return (
    <Box sx={{ p: 3, width: '100%' }}>
      <Typography variant="h4" sx={{ mb: 3 }}>
        User Permissions Management
      </Typography>

      {alertMessage && (
        <Alert severity={alertMessage.severity} sx={{ mb: 2 }}>
          {alertMessage.message}
        </Alert>
      )}

      {/* SUMMARY CARDS */}
      <Grid container spacing={2} sx={{ mb: 3 }}>
        {/* READ */}
        <Grid item xs={12} md={4}>
          <Card
            variant="outlined"
            sx={{ cursor: 'pointer', bgcolor: selectedCard === 'read' ? 'action.selected' : undefined }}
            onClick={() => setSelectedCard(selectedCard === 'read' ? null : 'read')}
          >
            <CardContent>
              <Typography gutterBottom>Read-Only Permissions</Typography>
              <Typography variant="h3">{readOnlyCount}</Typography>
            </CardContent>
          </Card>
        </Grid>

        {/* EDIT */}
        <Grid item xs={12} md={4}>
          <Card
            variant="outlined"
            sx={{ cursor: 'pointer', bgcolor: selectedCard === 'edit' ? 'action.selected' : undefined }}
            onClick={() => setSelectedCard(selectedCard === 'edit' ? null : 'edit')}
          >
            <CardContent>
              <Typography gutterBottom>Read & Edit Permissions</Typography>
              <Typography variant="h3">{editCount}</Typography>
            </CardContent>
          </Card>
        </Grid>

        {/* REQUESTS */}
        <Grid item xs={12} md={4}>
          <Card
            variant="outlined"
            sx={{ cursor: 'pointer', bgcolor: requestsExpanded ? 'action.selected' : undefined }}
            onClick={() => setRequestsExpanded(!requestsExpanded)}
          >
            <CardContent>
              <Typography gutterBottom>Edit Permission Requests</Typography>
              <Typography variant="h3">{requestsCount}</Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>

      {/* REQUESTS TABLE */}
      <Collapse in={requestsExpanded}>
        <Card sx={{ mb: 3 }}>
          <CardContent>
            <Typography variant="h6">Permission Requests</Typography>
            <Table>
              <TableBody>
                {requests.map(r => (
                  <TableRow key={r.id}>
                    <TableCell>{r.email}</TableCell>
                    <TableCell>
                      <Chip label={r.requestedPermission} />
                    </TableCell>
                    <TableCell align="right">
                      <IconButton onClick={() => handleApproveRequest(r)} color="success">
                        <CheckIcon />
                      </IconButton>
                      <IconButton onClick={() => handleDenyRequest(r)} color="error">
                        <CloseIcon />
                      </IconButton>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </Collapse>

      {/* CONTROLS */}
      <Box sx={{ display: 'flex', gap: 2, mb: 2, flexWrap: 'wrap' }}>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setAddDialogOpen(true)}>
          Add User
        </Button>

        <FormControl sx={{ minWidth: 200 }}>
          <InputLabel>Filter by Permission</InputLabel>
          <Select
            value={selectedCard ? 'all' : filterPermission}
            label="Filter by Permission"
            onChange={(e) => {
              setFilterPermission(e.target.value);
              setSelectedCard(null);
            }}
          >
            <MenuItem value="all">All</MenuItem>
            <MenuItem value="read">Read</MenuItem>
            <MenuItem value="edit">Edit</MenuItem>
          </Select>
        </FormControl>

        {selectedUsers.length > 0 && (
          <>
            <Button color="error" onClick={handleDeleteUsers}>
              Delete Selected ({selectedUsers.length})
            </Button>
            <Button onClick={() => handleBulkChangePermissions('read')}>Set Read</Button>
            <Button onClick={() => handleBulkChangePermissions('edit')}>Set Edit</Button>
          </>
        )}
      </Box>

      {/* USERS TABLE */}
      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell padding="checkbox">
                <Checkbox
                  checked={selectedUsers.length === filteredUsers.length && filteredUsers.length > 0}
                  indeterminate={selectedUsers.length > 0 && selectedUsers.length < filteredUsers.length}
                  onChange={handleSelectAll}
                />
              </TableCell>
              <TableCell>Email</TableCell>
              <TableCell>Permission</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {filteredUsers.map(user => (
              <TableRow key={user.id} hover>
                <TableCell padding="checkbox">
                  <Checkbox
                    checked={selectedUsers.includes(user.id)}
                    onChange={() => handleSelectUser(user.id)}
                  />
                </TableCell>
                <TableCell>{user.email}</TableCell>
                <TableCell>
                  <Chip label={user.permission} />
                </TableCell>
                <TableCell align="right">
                  <IconButton onClick={() => handleEditPermission(user)}>
                    <EditIcon />
                  </IconButton>
                  <IconButton color="error" onClick={() => handleDeleteUsers([user.id])}>
                    <DeleteIcon />
                  </IconButton>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>

      {/* ADD USER DIALOG */}
      <Dialog open={addDialogOpen} onClose={() => setAddDialogOpen(false)}>
        <DialogTitle>Add New User</DialogTitle>
        <DialogContent>

          <TextField
            fullWidth
            label="Email"
            type="email"
            required
            sx={{ mb: 2 }}
            value={newUser.email}
            onChange={e => setNewUser({ ...newUser, email: e.target.value })}
          />

          <TextField
            fullWidth
            label="Password"
            type="password"
            required
            sx={{ mb: 2 }}
            value={newUser.password}
            onChange={e => setNewUser({ ...newUser, password: e.target.value })}
          />

          <TextField
            fullWidth
            label="Confirm Password"
            type="password"
            required
            sx={{ mb: 2 }}
            value={newUser.confirmPassword}
            onChange={e => setNewUser({ ...newUser, confirmPassword: e.target.value })}
          />

          {passwordError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {passwordError}
            </Alert>
          )}

          <FormControl fullWidth>
            <InputLabel>Permission</InputLabel>
            <Select
              value={newUser.permission}
              label="Permission"
              onChange={e =>
                setNewUser({ ...newUser, permission: e.target.value })
              }
            >
              <MenuItem value="read">Read Only</MenuItem>
              <MenuItem value="edit">Read & Edit</MenuItem>
            </Select>
          </FormControl>

        </DialogContent>

        <DialogActions>
          <Button onClick={() => setAddDialogOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            onClick={handleAddUser}
            disabled={!newUser.email || !newUser.password}
          >
            Add User
          </Button>
        </DialogActions>
      </Dialog>

      {/* EDIT PERMISSION DIALOG */}
      <Dialog open={editDialogOpen} onClose={() => setEditDialogOpen(false)}>
        <DialogTitle>Change Permission</DialogTitle>
        <DialogContent>
          {editingUser && (
            <Select
              fullWidth
              value={editingUser.permission}
              onChange={e =>
                setEditingUser({ ...editingUser, permission: e.target.value })
              }
            >
              <MenuItem value="read">Read</MenuItem>
              <MenuItem value="edit">Edit</MenuItem>
            </Select>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleSavePermission}>
            Save
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
