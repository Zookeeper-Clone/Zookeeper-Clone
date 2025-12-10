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

export default function SignIn() {
  const [errorMessage, setErrorMessage] = useState("");
  const navigate = useNavigate();

  const getReadableError = (status, raw) => {
    if (status === 400) return "Invalid input. Please check your information.";
    if (status === 401) return "Invalid email or password.";
    if (status === 403) return "Unauthorized.";
    if (status >= 500) return "Server error. Try again later.";
    return raw || "Something went wrong.";
  };

  // Basic login
  const handleBasicLogin = async (e) => {
    e.preventDefault();
    setErrorMessage("");

    const form = new FormData(e.target);
    const email = form.get("email");
    const password = form.get("password");

    try {
      const response = await fetch("http://localhost:8080/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, password }),
        credentials: "include", // <-- important to store cookie
      });

      if (!response.ok) {
        const raw = await response.text();
        setErrorMessage(getReadableError(response.status, raw));
        return;
      }

      const body = await response.text();
      console.log("Login response:", body);

      localStorage.setItem("auth", "true");

      sessionStorage.setItem("sessionToken", "frontend-session-placeholder");

      // Navigate to protected page
      navigate("/home");
    } catch (err) {
      console.error(err);
      setErrorMessage("Network error. Please try again.");
    }
  };

  // OAuth login (MOCK)
  const handleGoogleLogin = async () => {
    setErrorMessage("");

    try {
      // Simulated Google login
      await new Promise((resolve) => setTimeout(resolve, 500));

      const googleEmail = "google.user@example.com";

      // Mock add user if doesn't exist
      const users = JSON.parse(sessionStorage.getItem("mockUsers") || "{}");
      if (!users[googleEmail]) {
        users[googleEmail] = { provider: "google" };
        sessionStorage.setItem("mockUsers", JSON.stringify(users));
      }

      // Store mock token + auth
      sessionStorage.setItem("sessionToken", "google-token-" + Date.now());
      sessionStorage.setItem("currentUser", googleEmail);
      localStorage.setItem("auth", "true");

      navigate("/home");
    } catch {
      setErrorMessage("Google login failed. Try again.");
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
          Sign in to My App
        </Typography>

        <Typography variant="body2" color="gray" textAlign="center" mb={3}>
          Welcome, please sign in to continue
        </Typography>

        {/* Google Sign-In Button */}
        <Button
          fullWidth
          onClick={handleGoogleLogin}
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
          Sign in with Google
        </Button>

        {/* OR */}
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
        <Box component="form" onSubmit={handleBasicLogin}>
          <TextField
            name="email"
            label="Email"
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
            InputLabelProps={{ style: { color: "#999" } }}
            InputProps={{ style: { color: "white" } }}
            sx={{ mb: 2 }}
          />

          {errorMessage && (
            <Typography
              color="error"
              fontSize="0.875rem"
              mb={2}
              sx={{
                bgcolor: "rgba(211, 47, 47, 0.1)",
                p: 1.5,
                borderRadius: 1,
                border: "1px solid rgba(211, 47, 47, 0.3)",
              }}
            >
              {errorMessage}
            </Typography>
          )}

          {/* Email sign-in button */}
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
            Sign in
          </Button>
        </Box>

        {/* Sign Up Link */}
        <Typography textAlign="center" fontSize="0.9rem" color="gray">
          Don’t have an account?{" "}
          <Link
            href="/register"
            underline="hover"
            color="#1976d2"
            fontWeight={600}
            sx={{ cursor: "pointer" }}
          >
            Sign up
          </Link>
        </Typography>
      </Paper>
    </Box>
  );
}