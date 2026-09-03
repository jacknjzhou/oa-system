import apiClient from './client'
import type { InstanceDTO, InstanceDetail, StartInstancePayload } from '../types'

/** 发起流程实例 */
export async function startInstance(payload: StartInstancePayload): Promise<InstanceDTO> {
  const response = await apiClient.post<InstanceDTO>('/process-instances', payload)
  return response.data
}

/** 我发起的实例列表 */
export async function getMyInstances(): Promise<InstanceDTO[]> {
  const response = await apiClient.get<InstanceDTO[]>('/process-instances/my')
  return response.data
}

/** 实例详情（含 BPMN XML、节点高亮与审批记录） */
export async function getInstance(id: string): Promise<InstanceDetail> {
  const response = await apiClient.get<InstanceDetail>(`/process-instances/${id}`)
  return response.data
}

/** 取消流程实例（发起人） */
export async function cancelInstance(id: string): Promise<InstanceDTO> {
  const response = await apiClient.post<InstanceDTO>(`/process-instances/${id}/cancel`)
  return response.data
}
