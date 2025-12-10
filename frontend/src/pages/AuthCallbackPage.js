import { useEffect } from "react";
import { useNavigate } from "react-router-dom";

export default function AuthCallback() {
  const navigate = useNavigate();

  useEffect(() => {
    // backend already set SESSION_TOKEN cookie
    localStorage.setItem("auth", "true");
    sessionStorage.setItem("sessionToken", "google-session-placeholder");

    navigate("/home");
  }, []);

  return <div>Signing you in...</div>; 
}
