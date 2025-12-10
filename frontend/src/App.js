import { BrowserRouter as Router, Routes, Route } from "react-router-dom";
import SignIn from "./pages/SignIn";
import Register from "./pages/Register";
import Home from "./pages/Home";
import ProtectedRoute from "./ProtectedRoute";

export default function App() {
  return (
    <Router>
      <Routes>
        <Route index element={<SignIn />} />
        <Route path="/login" element={<SignIn />} />
        <Route path="/register" element={<Register />} />

        {/* PROTECTED ROUTE */}
        <Route
          path="/home"
          element={
            <ProtectedRoute>
              <Home />
            </ProtectedRoute>
          }
        />
      </Routes>
    </Router>
  );
}
