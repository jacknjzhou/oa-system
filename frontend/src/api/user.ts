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
  phone?: string | null
  employeeNo?: string | null
  gender?: string | null
  supervisorId?: number | null
  orgId?: number | null
  orgName?: string | null
  roles?: string[]
  jobLevelId?: number | null
  jobTitleId?: number | null
  jobTitleName?: string | null
  lastLoginAt?: string | null
  status: 'ACTIVE' | 'INACTIVE' | 'LOCKED' | 'DELETED'
  deletedAt?: string | null
}

export interface UserListParams {
  status?: 'active' | 'disabled' | 'deleted' | 'all'
  orgId?: number | null
  keyword?: string | null
}

export interface UserCreateBody {
  username: string
  password: string
  realName: string
  gender?: string | null
  position?: string | null
  phone?: string | null
  email?: string | null
  orgId?: number | null
  supervisorId?: number | null
  jobLevelId?: number | null
  jobTitleId?: number | null
  roleCodes?: string[]
}

export interface UserUpdateBody {
  realName?: string | null
  position?: string | null
  phone?: string | null
  email?: string | null
  gender?: string | null
  supervisorId?: number | null
  orgId?: number | null
  clearOrg?: boolean
  jobLevelId?: number | null
  clearJobLevel?: boolean
  jobTitleId?: number | null
  clearJobTitle?: boolean
  roleCodes?: string[]
}

export interface DictRow {
  id: number
  code: string
  name: string
  enabled: boolean
}

export interface DictCreateBody {
  code?: string | null
  name: string
  enabled?: boolean
}

export interface JobLevelRow {
  id: number
  code: string
  name: string
  enabled: boolean
}

export const userApi = {
  /** 默认 status=active（选人语义）；管理页显式传 status/orgId/keyword。 */
  list: (params?: UserListParams | boolean) => {
    const p: UserListParams = params === undefined ? {} : typeof params === 'boolean' ? { status: 'active' } : params
    const q: Record<string, string | number> = {}
    if (p.status) q.status = p.status
    if (p.orgId != null) q.orgId = p.orgId
    if (p.keyword) q.keyword = p.keyword
    return apiClient.get<UserRow[]>('/users', { params: q }).then((r) => r.data)
  },
  create: (body: UserCreateBody) => apiClient.post<UserRow>('/users', body).then((r) => r.data),
  disable: (id: number) => apiClient.post<UserRow>(`/users/${id}/disable`).then((r) => r.data),
  enable: (id: number) => apiClient.post<UserRow>(`/users/${id}/enable`).then((r) => r.data),
  remove: (id: number) => apiClient.delete<UserRow>(`/users/${id}`).then((r) => r.data),
  restore: (id: number) => apiClient.post<UserRow>(`/users/${id}/restore`).then((r) => r.data),
  update: (id: number, body: UserUpdateBody) => apiClient.put<UserRow>(`/users/${id}`, body).then((r) => r.data),
  jobLevels: (all = true) =>
    apiClient.get<JobLevelRow[]>('/job-levels', { params: { all } }).then((r) => r.data),
  createJobLevel: (body: DictCreateBody) => apiClient.post<JobLevelRow>('/job-levels', body).then((r) => r.data),
  updateJobLevel: (id: number, body: Partial<DictCreateBody>) =>
    apiClient.put<JobLevelRow>(`/job-levels/${id}`, body).then((r) => r.data),
  jobTitles: (all = true) => apiClient.get<DictRow[]>('/job-titles', { params: { all } }).then((r) => r.data),
  createJobTitle: (body: DictCreateBody) => apiClient.post<DictRow>('/job-titles', body).then((r) => r.data),
  updateJobTitle: (id: number, body: Partial<DictCreateBody>) =>
    apiClient.put<DictRow>(`/job-titles/${id}`, body).then((r) => r.data),
}
