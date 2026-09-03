import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import type { ApiResponse } from '../types'

export const STORAGE_KEYS = {
  accessToken: 'accessToken',
  refreshToken: 'refreshToken',
  userInfo: 'userInfo',
} as const

const apiClient = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 请求拦截器：附加 Bearer Token
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem(STORAGE_KEYS.accessToken)
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器：解包统一响应结构 { code, message, data }，401 清除凭证跳登录页
apiClient.interceptors.response.use(
  (response) => {
    const body = response.data as Partial<ApiResponse> | undefined
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code !== 0 && body.code !== 200) {
        return Promise.reject(new Error(body.message || '请求失败'))
      }
      // 统一解包：调用方直接拿到 data
      response.data = body.data
    }
    return response
  },
  (error: AxiosError<{ message?: string }>) => {
    if (error.response?.status === 401) {
      localStorage.removeItem(STORAGE_KEYS.accessToken)
      localStorage.removeItem(STORAGE_KEYS.refreshToken)
      localStorage.removeItem(STORAGE_KEYS.userInfo)
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    const message = error.response?.data?.message || error.message || '网络异常，请稍后重试'
    return Promise.reject(new Error(message))
  }
)

export default apiClient
