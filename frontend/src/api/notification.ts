import apiClient from './client'
import type { Notification } from '../types'

/** 我的通知列表（未读优先，其次时间倒序） */
export async function getMyNotifications(limit = 20): Promise<Notification[]> {
  const response = await apiClient.get<Notification[]>('/notifications', { params: { limit } })
  return response.data
}

/** 未读通知数（铃铛角标） */
export async function getUnreadCount(): Promise<number> {
  const response = await apiClient.get<{ count: number }>('/notifications/unread-count')
  return response.data.count
}

/** 标记单条已读 */
export async function markNotificationRead(id: number): Promise<void> {
  await apiClient.post(`/notifications/${id}/read`)
}

/** 全部标记已读 */
export async function markAllNotificationsRead(): Promise<void> {
  await apiClient.post('/notifications/read-all')
}
