import apiClient, { STORAGE_KEYS } from './client'
import type { LoginResult, UserInfo } from '../types'

/** 登录并持久化凭证 */
export async function login(username: string, password: string): Promise<LoginResult> {
  const response = await apiClient.post<LoginResult>('/auth/login', { username, password })
  const result = response.data
  localStorage.setItem(STORAGE_KEYS.accessToken, result.accessToken)
  localStorage.setItem(STORAGE_KEYS.refreshToken, result.refreshToken)
  localStorage.setItem(STORAGE_KEYS.userInfo, JSON.stringify(result.userInfo))
  return result
}

/** 刷新 Token */
export async function refreshToken(): Promise<LoginResult> {
  const rt = localStorage.getItem(STORAGE_KEYS.refreshToken)
  if (!rt) {
    throw new Error('登录状态已失效，请重新登录')
  }
  const response = await apiClient.post<LoginResult>('/auth/refresh', { refreshToken: rt })
  const result = response.data
  localStorage.setItem(STORAGE_KEYS.accessToken, result.accessToken)
  localStorage.setItem(STORAGE_KEYS.refreshToken, result.refreshToken)
  if (result.userInfo) {
    localStorage.setItem(STORAGE_KEYS.userInfo, JSON.stringify(result.userInfo))
  }
  return result
}

/** 退出登录：无论接口是否成功都清理本地凭证 */
export async function logout(): Promise<void> {
  try {
    await apiClient.post('/auth/logout')
  } catch {
    // 登出接口失败不阻塞本地凭证清理
  } finally {
    localStorage.removeItem(STORAGE_KEYS.accessToken)
    localStorage.removeItem(STORAGE_KEYS.refreshToken)
    localStorage.removeItem(STORAGE_KEYS.userInfo)
  }
}

export function getToken(): string | null {
  return localStorage.getItem(STORAGE_KEYS.accessToken)
}

export function getStoredUser(): UserInfo | null {
  const raw = localStorage.getItem(STORAGE_KEYS.userInfo)
  if (!raw) return null
  try {
    return JSON.parse(raw) as UserInfo
  } catch {
    return null
  }
}
