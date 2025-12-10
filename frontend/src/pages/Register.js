import React, { useState } from "react";
import {
  Box,
  Paper,
  Typography,
  TextField,
  Button,
  Divider,
  Link,
} from "@mui/material";
import { useNavigate } from "react-router-dom";

export default function Register() {
  const navigate = useNavigate();

  const [errorMessage, setErrorMessage] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [passwordError, setPasswordError] = useState("");

  const getReadableError = (status, raw) => {
    if (status === 400) return "Invalid input. Please check your information.";
    if (status === 409) return "An account with this email already exists.";
    if (status === 403) return "Unauthorized.";
    if (status >= 500) return "Server error. Try again later.";
    return raw || "Something went wrong.";
  };

  const validatePasswords = () => {
    if (password !== confirmPassword) {
      setPasswordError("Passwords do not match.");
      return false;
    }
    if (password.length < 8) {
      setPasswordError("Password must be at least 8 characters.");
      return false;
    }
    setPasswordError("");
    return true;
  };

  // Basic register
  const handleBasicRegister = async (e) => {
    e.preventDefault();
    setErrorMessage("");
    setPasswordError("");

    if (!validatePasswords()) return;

    const form = new FormData(e.target);
    const email = form.get("email");

    try {
      const response = await fetch("http://localhost:8080/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
      });

      if (!response.ok) {
        const raw = await response.text();
        setErrorMessage(getReadableError(response.status, raw));
        return;
      }

      const data = await response.json();
      sessionStorage.setItem("sessionToken", data.token);

      navigate("/home"); // ⬅️ redirect via React Router
    } catch {
      setErrorMessage("Network error. Please try again.");
    }
  };

  // OAuth register (MOCK)
  const handleOAuthRegister = async () => {
    setErrorMessage("");

    try {
      const email = "google@example.com";
      const token = "GOOGLE_OAUTH_TOKEN";

      const response = await fetch("http://localhost:8080/auth/registerOAuth", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, token }),
      });

      if (!response.ok) {
        const raw = await response.text();
        setErrorMessage(getReadableError(response.status, raw));
        return;
      }

      const data = await response.json();
      sessionStorage.setItem("sessionToken", data.token);

      navigate("/home");
    } catch {
      setErrorMessage("Network error. Please try again.");
    }
  };

  return (
    <Box
      sx={{
        height: "100vh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        bgcolor: "#0D0D0D",
        p: 2,
      }}
    >
      <Paper
        elevation={3}
        sx={{
          p: 4,
          width: "100%",
          maxWidth: 380,
          bgcolor: "#111111",
          color: "white",
          borderRadius: 2,
        }}
      >
        {/* Title */}
        <Typography variant="h6" fontWeight={600} textAlign="center" mb={1}>
          Create your account
        </Typography>

        <Typography variant="body2" color="gray" textAlign="center" mb={3}>
          Sign up to get started
        </Typography>

        {/* Google Sign-Up Button */}
        <Button
          fullWidth
          onClick={handleOAuthRegister}
          sx={{
            bgcolor: "white",
            color: "black",
            textTransform: "none",
            mb: 2,
            py: 1.2,
            borderRadius: 1.2,
            fontWeight: 600,
            "&:hover": { bgcolor: "#f0f0f0" },
            display: "flex",
            gap: 1,
            alignItems: "center",
            justifyContent: "center",
          }}
        >
          <img
            src="https://www.gstatic.com/firebasejs/ui/2.0.0/images/auth/google.svg"
            alt="Google"
            style={{ width: 20, height: 20 }}
          />
          Sign up with Google
        </Button>

        <Divider
          sx={{
            my: 3,
            borderColor: "#333",
            "&::before, &::after": {
              borderColor: "#333",
            },
          }}
        >
          <Typography color="gray" variant="body2">
            or
          </Typography>
        </Divider>

        {/* Email/Password Form */}
        <Box component="form" onSubmit={handleBasicRegister}>
          <TextField
            name="email"
            label="your@email.com"
            type="email"
            variant="standard"
            fullWidth
            required
            InputLabelProps={{ style: { color: "#999" } }}
            InputProps={{ style: { color: "white" } }}
            sx={{ mb: 3 }}
          />

          <TextField
            name="password"
            label="Password"
            type="password"
            variant="standard"
            fullWidth
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            InputLabelProps={{ style: { color: "#999" } }}
            InputProps={{ style: { color: "white" } }}
            sx={{ mb: 3 }}
          />

          <TextField
            name="confirmPassword"
            label="Re-enter Password"
            type="password"
            variant="standard"
            fullWidth
            required
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            InputLabelProps={{ style: { color: "#999" } }}
            InputProps={{ style: { color: "white" } }}
            sx={{ mb: 2 }}
          />

          {passwordError && (
            <Typography
              color="error"
              fontSize="0.875rem"
              mb={2}
              sx={{
                bgcolor: "rgba(211, 47, 47, 0.1)",
                p: 1.5,
                borderRadius: 1,
                border: "1px solid rgba(211, 47, 47, 0.3)"
              }}
            >
              {passwordError}
            </Typography>
          )}

          {errorMessage && (
            <Typography
              color="error"
              fontSize="0.875rem"
              mb={2}
              sx={{
                bgcolor: "rgba(211, 47, 47, 0.1)",
                p: 1.5,
                borderRadius: 1,
                border: "1px solid rgba(211, 47, 47, 0.3)"
              }}
            >
              {errorMessage}
            </Typography>
          )}

          {/* Sign Up Button */}
          <Button
            type="submit"
            fullWidth
            variant="contained"
            sx={{
              py: 1.2,
              borderRadius: 1.2,
              fontWeight: 600,
              mb: 2,
            }}
          >
            Sign up
          </Button>
        </Box>

        {/* Sign In Link */}
        <Typography textAlign="center" fontSize="0.9rem" color="gray">
          Already have an account?{" "}
          <Link
            href="/login"
            underline="hover"
            color="#1976d2"
            fontWeight={600}
            sx={{ cursor: "pointer" }}
          >
            Sign in
          </Link>
        </Typography>
      </Paper>
    </Box>
  );
}
