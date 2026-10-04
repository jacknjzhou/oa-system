import apiClient from './client'
import type { ApprovalType } from '../types'

export interface ApprovalTypePayload {
  code: string
  name: string
  category?: string
  icon?: string
  description?: string
  weight?: number
  defId: string
  enabled?: boolean
}

/** 全部审批类型（管理页） */
export async function getApprovalTypes(): Promise<ApprovalType[]> {
  const response = await apiClient.get<ApprovalType[]>('/approval-types')
  return response.data
}

/** 仅启用（发起页） */
export async function getEnabledApprovalTypes(): Promise<ApprovalType[]> {
  const response = await apiClient.get<ApprovalType[]>('/approval-types/enabled')
  return response.data
}

export async function createApprovalType(payload: ApprovalTypePayload): Promise<ApprovalType> {
  const response = await apiClient.post<ApprovalType>('/approval-types', payload)
  return response.data
}

export async function updateApprovalType(id: string, payload: ApprovalTypePayload): Promise<ApprovalType> {
  const response = await apiClient.put<ApprovalType>(`/approval-types/${id}`, payload)
  return response.data
}

export async function deleteApprovalType(id: string): Promise<void> {
  await apiClient.delete(`/approval-types/${id}`)
}
