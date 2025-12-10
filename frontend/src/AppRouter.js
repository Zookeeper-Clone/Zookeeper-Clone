import { BrowserRouter as Router, Routes, Route, Link, useNavigate } from "react-router-dom";
import { useState } from "react";

export default function App() {
  return (
    <Router>
      <Routes>
        <Route path="/" element={<SignIn />} />
        <Route path="/login" element={<SignIn />} />
        <Route path="/register" element={<Register />} />
        <Route path="/home" element={<Home />} />
      </Routes>
    </Router>
  );
}

import SignIn from "./pages/SignIn";
import Register from "./pages/Register";
import Home from "./pages/Home";
      >
        Sign In
      </button>
      <Link to="/register" className="underline text-blue-600">Sign Up</Link>
    </div>
  );
}

function Register() {
  const navigate = useNavigate();

  return (
    <div className="flex flex-col items-center justify-center h-screen gap-4">
      <h1 className="text-2xl font-bold">Register</h1>
      <button
        className="px-4 py-2 rounded-xl shadow"
        onClick={() => navigate("/home")}
      >
        Create Account
      </button>
      <Link to="/login" className="underline text-blue-600">Back to Login</Link>
    </div>
  );
}

function Home() {
  return (
    <div className="flex items-center justify-center h-screen">
      <h1 className="text-3xl font-bold">Welcome Home!</h1>
    </div>
  );
}
