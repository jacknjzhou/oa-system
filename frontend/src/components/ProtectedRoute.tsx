import { Navigate } from 'react-router-dom'
import type { ReactNode } from 'react'
import { getToken, getStoredUser } from '../api/auth'

interface ProtectedRouteProps {
  children: ReactNode
  /** 需要的权限码（任一匹配即可）；ADMIN 角色直通 */
  perm?: string | string[]
}

export default function ProtectedRoute({ children, perm }: ProtectedRouteProps) {
  const token = getToken()

  if (!token) {
    return <Navigate to="/login" replace />
  }

  if (perm) {
    const user = getStoredUser()
    const required = Array.isArray(perm) ? perm : [perm]
    const roles = user?.roles ?? []
    const permissions = user?.permissions ?? []
    const ok = roles.includes('ADMIN') || required.some((code) => permissions.includes(code))
    if (!ok) {
      return <Navigate to="/start" replace />
    }
  }

  return <>{children}</>
}
