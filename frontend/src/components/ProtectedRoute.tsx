import { Navigate } from 'react-router-dom'
import type { ReactNode } from 'react'
import { getToken } from '../api/auth'

interface ProtectedRouteProps {
  children: ReactNode
}

export default function ProtectedRoute({ children }: ProtectedRouteProps) {
  const token = getToken()

  if (!token) {
    return <Navigate to="/login" replace />
  }

  return <>{children}</>
}
