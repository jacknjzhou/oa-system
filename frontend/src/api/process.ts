import apiClient from './client'
import type { ApiResponse, ProcessDefinition, ProcessInstance, StartProcessRequest } from '../types'

// Mock 流程定义
const mockDefinitions: ProcessDefinition[] = [
  {
    id: 'pd-1',
    key: 'expense_approval',
    name: '费用报销审批流程',
    version: 3,
    description: '员工费用报销申请审批流程，含部门经理、财务、总经理审批节点',
    deployedAt: '2026-08-01 10:00',
  },
  {
    id: 'pd-2',
    key: 'procurement_approval',
    name: '采购申请审批流程',
    version: 2,
    description: '采购申请审批流程，含部门经理、财务、分管副总审批节点',
    deployedAt: '2026-07-15 14:00',
  },
  {
    id: 'pd-3',
    key: 'contract_approval',
    name: '合同审批流程',
    version: 4,
    description: '合同签订审批流程，含法务审核、部门经理、总经理审批节点',
    deployedAt: '2026-06-20 09:00',
  },
]

// Mock 流程实例
const mockInstance: ProcessInstance = {
  id: 'pi-1',
  serialNo: 'OA2024-0002',
  definitionId: 'pd-2',
  definitionName: '采购申请审批流程',
  businessKey: 'PR2024-0002',
  title: '采购申请审批',
  status: 'running',
  initiator: '李四',
  initiatorId: 'u2',
  currentNode: '财务审批',
  currentNodeKey: 'finance',
  startTime: '2026-08-27 14:20',
  duration: '2天3小时',
  nodes: [
    {
      id: 'n1',
      name: '发起',
      type: 'start',
      status: 'completed',
      operator: '李四',
      action: '提交申请',
      comment: '请审批',
      time: '2026-08-27 14:20',
    },
    {
      id: 'n2',
      name: '部门经理',
      type: 'approval',
      assignee: '王经理',
      status: 'completed',
      operator: '王经理',
      action: '同意',
      comment: '同意采购申请',
      time: '2026-08-27 16:00',
    },
    {
      id: 'n3',
      name: '财务',
      type: 'approval',
      assignee: '刘会计',
      status: 'current',
    },
    {
      id: 'n4',
      name: '总经理',
      type: 'approval',
      assignee: '总经理',
      status: 'pending',
    },
    {
      id: 'n5',
      name: '结束',
      type: 'end',
      status: 'pending',
    },
  ],
  records: [
    {
      id: 'r1',
      step: 1,
      node: '发起',
      operator: '李四',
      operatorId: 'u2',
      action: '提交申请',
      comment: '申请采购办公设备，共计 ¥12,800',
      time: '2026-08-27 14:20',
    },
    {
      id: 'r2',
      step: 2,
      node: '部门经理审批',
      operator: '王经理',
      operatorId: 'u5',
      action: '同意',
      comment: '情况属实，同意采购。',
      time: '2026-08-27 16:00',
    },
    {
      id: 'r3',
      step: 3,
      node: '财务审批',
      operator: '刘会计',
      operatorId: 'u6',
      action: '审批中',
      comment: '',
      time: '2026-08-28 09:00',
    },
  ],
}

// 获取流程定义列表
export async function getProcessDefinitions(): Promise<ProcessDefinition[]> {
  try {
    const response = await apiClient.get<ApiResponse<ProcessDefinition[]>>('/process/definitions')
    return response.data.data
  } catch {
    return mockDefinitions
  }
}

// 启动流程实例
export async function startProcessInstance(data: StartProcessRequest): Promise<ProcessInstance> {
  try {
    const response = await apiClient.post<ApiResponse<ProcessInstance>>('/process/instances', data)
    return response.data.data
  } catch {
    return {
      ...mockInstance,
      id: 'pi-' + Date.now(),
      serialNo: 'OA2024-' + String(Date.now()).slice(-4),
      title: data.title,
      definitionId: data.processDefinitionKey,
      startTime: new Date().toISOString(),
    }
  }
}

// 获取流程实例详情
export async function getProcessInstance(id: string): Promise<ProcessInstance> {
  try {
    const response = await apiClient.get<ApiResponse<ProcessInstance>>(`/process/instances/${id}`)
    return response.data.data
  } catch {
    return { ...mockInstance, id }
  }
}

// 取消流程实例
export async function cancelProcessInstance(id: string): Promise<void> {
  try {
    await apiClient.post(`/process/instances/${id}/cancel`)
  } catch {
    // Mock: 静默成功
  }
}

// 获取我发起的流程实例
export async function getMyInstances(): Promise<ProcessInstance[]> {
  try {
    const response = await apiClient.get<ApiResponse<ProcessInstance[]>>('/process/instances/mine')
    return response.data.data
  } catch {
    return [mockInstance]
  }
}
