import apiClient from './client'
import type { Template, TemplateCreatePayload, TemplateUpdatePayload } from '../types'

/** 模板列表（deployable=true 时仅返回已发布可发起的） */
export async function getTemplates(deployable?: boolean): Promise<Template[]> {
  const response = await apiClient.get<Template[]>('/process-definitions', {
    params: deployable === undefined ? undefined : { deployable },
  })
  return response.data
}

/** 模板详情（含 bpmnXml） */
export async function getTemplate(id: string): Promise<Template> {
  const response = await apiClient.get<Template>(`/process-definitions/${id}`)
  return response.data
}

/** 新建模板 */
export async function createTemplate(payload: TemplateCreatePayload): Promise<Template> {
  const response = await apiClient.post<Template>('/process-definitions', payload)
  return response.data
}

/** 更新模板（XML 变化时版本 +1 并重新部署） */
export async function updateTemplate(id: string, payload: TemplateUpdatePayload): Promise<Template> {
  const response = await apiClient.put<Template>(`/process-definitions/${id}`, payload)
  return response.data
}

/** 发布模板 */
export async function publishTemplate(id: string): Promise<Template> {
  const response = await apiClient.post<Template>(`/process-definitions/${id}/publish`)
  return response.data
}

/** 停用模板 */
export async function disableTemplate(id: string): Promise<Template> {
  const response = await apiClient.post<Template>(`/process-definitions/${id}/disable`)
  return response.data
}

export interface ApprovalPermissionRow {
  roleCode: string
  defName?: string
  perm: 'start' | 'view' | 'manage' | 'edit'
}

export interface ApprovalPermissionPayload {
  roleCode: string
  perms: string[]
}

/** 审批功能权限列表（P2-3） */
export async function getTemplatePermissions(id: string): Promise<ApprovalPermissionRow[]> {
  const response = await apiClient.get<ApprovalPermissionRow[]>(`/process-definitions/${id}/permissions`)
  return response.data
}

/** 保存审批功能权限（整体替换） */
export async function saveTemplatePermissions(id: string, rows: ApprovalPermissionPayload[]): Promise<{ defId: string; count: number }> {
  const response = await apiClient.put<{ defId: string; count: number }>(`/process-definitions/${id}/permissions`, rows)
  return response.data
}
