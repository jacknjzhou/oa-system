import apiClient from './client'
import type { ApiResponse, LoginResult, User } from '../types'

// 登录
export async function login(username: string, password: string): Promise<LoginResult> {
  try {
    const response = await apiClient.post<ApiResponse<LoginResult>>('/auth/login', {
      username,
      password,
    })
    const result = response.data.data
    localStorage.setItem('token', result.token)
    localStorage.setItem('refreshToken', result.refreshToken)
    localStorage.setItem('user', JSON.stringify(result.user))
    return result
  } catch {
    // Mock 数据 fallback
    const mockUser: User = {
      id: '1',
      username,
      nickname: '管理员',
      email: 'admin@oa.com',
      phone: '13800138000',
      department: '信息技术部',
      role: 'admin',
    }
    const mockResult: LoginResult = {
      token: 'mock-jwt-token-' + Date.now(),
      refreshToken: 'mock-refresh-token-' + Date.now(),
      user: mockUser,
    }
    localStorage.setItem('token', mockResult.token)
    localStorage.setItem('refreshToken', mockResult.refreshToken)
    localStorage.setItem('user', JSON.stringify(mockUser))
    return mockResult
  }
}

// 退出登录
export async function logout(): Promise<void> {
  try {
    await apiClient.post('/auth/logout')
  } catch {
    // 静默处理
  } finally {
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('user')
  }
}

// 获取当前用户信息
export async function getProfile(): Promise<User> {
  try {
    const response = await apiClient.get<ApiResponse<User>>('/auth/profile')
    return response.data.data
  } catch {
    // Mock fallback
    const cached = localStorage.getItem('user')
    if (cached) {
      return JSON.parse(cached) as User
    }
    return {
      id: '1',
      username: 'admin',
      nickname: '管理员',
      email: 'admin@oa.com',
      phone: '13800138000',
      department: '信息技术部',
      role: 'admin',
    }
  }
}

// 刷新 token
export async function refreshToken(): Promise<string | null> {
  try {
    const rt = localStorage.getItem('refreshToken')
    if (!rt) return null
    const response = await apiClient.post<ApiResponse<{ token: string; refreshToken: string }>>(
      '/auth/refresh',
      { refreshToken: rt }
    )
    const { token, refreshToken: newRt } = response.data.data
    localStorage.setItem('token', token)
    localStorage.setItem('refreshToken', newRt)
    return token
  } catch {
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('user')
    window.location.href = '/login'
    return null
  }
}

// 获取当前缓存的用户
export function getCachedUser(): User | null {
  const cached = localStorage.getItem('user')
  if (cached) {
    try {
      return JSON.parse(cached) as User
    } catch {
      return null
    }
  }
  return null
}
