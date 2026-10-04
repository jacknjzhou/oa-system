import apiClient from './client'
import type { Role, UserSummary } from '../types'

/** 获取用户列表 */
export async function getUsers(): Promise<UserSummary[]> {
  const response = await apiClient.get<UserSummary[]>('/users')
  return response.data
}

/** 获取角色列表 */
export async function getRoles(): Promise<Role[]> {
  const response = await apiClient.get<Role[]>('/roles')
  return response.data
}

/** 员工管理（SY-01）：含状态与回收站 */
export interface UserRow {
  id: number
  username: string
  realName: string | null
  position?: string | null
  email?: string | null
  supervisorId?: number | null
  roles?: string[]
  status: 'ACTIVE' | 'INACTIVE' | 'LOCKED' | 'DELETED'
  deletedAt?: string | null
}

export const userApi = {
  list: (includeDeleted = false) =>
    apiClient.get<UserRow[]>('/users', { params: { includeDeleted } }).then((r) => r.data),
  disable: (id: number) => apiClient.post<UserRow>(`/users/${id}/disable`).then((r) => r.data),
  enable: (id: number) => apiClient.post<UserRow>(`/users/${id}/enable`).then((r) => r.data),
  remove: (id: number) => apiClient.delete<UserRow>(`/users/${id}`).then((r) => r.data),
  restore: (id: number) => apiClient.post<UserRow>(`/users/${id}/restore`).then((r) => r.data),
}
