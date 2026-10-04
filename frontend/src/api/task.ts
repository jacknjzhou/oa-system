import apiClient from './client'
import type { TaskDTO, TaskDetail } from '../types'

/** 我的待办任务 */
export async function getTodoTasks(): Promise<TaskDTO[]> {
  const response = await apiClient.get<TaskDTO[]>('/tasks')
  return response.data
}

/** 我已办结的历史任务 */
export async function getDoneTasks(): Promise<TaskDTO[]> {
  const response = await apiClient.get<TaskDTO[]>('/tasks/done')
  return response.data
}

/** 任务详情（含实例、BPMN XML、节点高亮与审批记录） */
export async function getTask(id: string): Promise<TaskDetail> {
  const response = await apiClient.get<TaskDetail>(`/tasks/${id}`)
  return response.data
}

/** 通过 */
export async function completeTask(id: string, payload: { comment: string }): Promise<void> {
  await apiClient.post(`/tasks/${id}/complete`, payload)
}

/** 驳回（toNodeKey 为空 = 整单驳回结束） */
export async function rejectTask(
  id: string,
  payload: { comment: string; toNodeKey?: string }
): Promise<void> {
  await apiClient.post(`/tasks/${id}/reject`, payload)
}

/** 拒绝：终止整个流程（区别于驳回到节点） */
export async function denyTask(
  id: string,
  payload: { comment: string }
): Promise<void> {
  await apiClient.post(`/tasks/${id}/deny`, payload)
}

/** 转办 */
export async function transferTask(
  id: string,
  payload: { toUserId: string; comment: string }
): Promise<void> {
  await apiClient.post(`/tasks/${id}/transfer`, payload)
}

/** 催办：发起人提醒当前审批人（可重复） */
export async function remindTask(id: string): Promise<void> {
  await apiClient.post(`/tasks/${id}/remind`)
}
