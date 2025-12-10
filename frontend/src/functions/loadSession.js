// src/api/auth.js

export async function loadSession() {
  try {
    const response = await fetch("http://localhost:8080/auth/me", {
      credentials: "include", // must include cookies
    });

    if (!response.ok) {
      return { success: false };
    }

    const data = await response.json();

    // Store locally if needed
    localStorage.setItem("auth", "true");
    localStorage.setItem("token", data.token);


    return {
      success: true,
      user: data,
    };
  } catch (err) {
    console.error("loadSession error:", err);
    return { success: false };
  }
}
