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
