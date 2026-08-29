import { Routes, Route, Navigate } from 'react-router-dom'
import Login from './pages/Login'
import TodoList from './pages/TodoList'
import ApprovalForm from './pages/ApprovalForm'
import ProcessTracking from './pages/ProcessTracking'
import ProtectedRoute from './components/ProtectedRoute'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route
        path="/"
        element={
          <ProtectedRoute>
            <TodoList />
          </ProtectedRoute>
        }
      />
      <Route
        path="/task/:id"
        element={
          <ProtectedRoute>
            <ApprovalForm />
          </ProtectedRoute>
        }
      />
      <Route
        path="/tracking/:id"
        element={
          <ProtectedRoute>
            <ProcessTracking />
          </ProtectedRoute>
        }
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
