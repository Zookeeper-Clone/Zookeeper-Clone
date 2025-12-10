import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { loadSession } from "../functions/loadSession";

export default function AuthCallback() {
  const navigate = useNavigate();

  useEffect(() => {
    
    loadSession();
    navigate('/home')
  }, []);

  return <div>Signing you in...</div>;
}