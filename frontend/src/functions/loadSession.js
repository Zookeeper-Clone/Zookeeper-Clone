// src/api/auth.js

export async function loadSession(email) {
  try {
    const response = await fetch("http://localhost:8080/auth/me", {
      credentials: "include", // must include cookies
    });

    if (!response.ok) {
      return { success: false };
    }

    const data = await response.json();

    localStorage.setItem("auth", "true");
    localStorage.setItem("token", data.token);

    const permissionsResponse = await fetch(`http://localhost:8080/users/${email}/permissions`, {
      credentials: 'include'
    })
    
    if (!permissionsResponse.ok){
      return {success: false};
    }

    const permissions = await permissionsResponse.json()


    localStorage.setItem("permissions", JSON.stringify(permissions))

    return {
      success: true,
      user: data,
    };
  } catch (err) {
    console.error("loadSession error:", err);
    return { success: false };
  }
}
