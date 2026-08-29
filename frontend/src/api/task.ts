import apiClient from './client'
import type { ApiResponse, Task, TaskDetail } from '../types'

// Mock 任务列表
const mockTasks: Task[] = [
  {
    id: '1',
    serialNo: 'OA2024-0001',
    title: '差旅费报销审批',
    initiator: '张三',
    initiatorDept: '市场部',
    type: '费用报销',
    amount: 3500,
    priority: 'normal',
    status: 'pending',
    deadline: '2026-09-05 18:00',
    createdAt: '2026-08-28 09:30',
    currentNode: '部门经理审批',
    currentNodeKey: 'dept_manager',
  },
  {
    id: '2',
    serialNo: 'OA2024-0002',
    title: '采购申请审批',
    initiator: '李四',
    initiatorDept: '行政部',
    type: '采购申请',
    amount: 12800,
    priority: 'high',
    status: 'processing',
    deadline: '2026-09-02 12:00',
    createdAt: '2026-08-27 14:20',
    currentNode: '财务审批',
    currentNodeKey: 'finance',
  },
  {
    id: '3',
    serialNo: 'OA2024-0003',
    title: '合同用印审批',
    initiator: '王五',
    initiatorDept: '法务部',
    type: '合同审批',
    amount: 0,
    priority: 'urgent',
    status: 'pending',
    deadline: '2026-08-30 18:00',
    createdAt: '2026-08-29 08:00',
    currentNode: '总经理审批',
    currentNodeKey: 'general_manager',
  },
  {
    id: '4',
    serialNo: 'OA2024-0004',
    title: '办公设备购置申请',
    initiator: '赵六',
    initiatorDept: '技术部',
    type: '采购申请',
    amount: 56000,
    priority: 'normal',
    status: 'overdue',
    deadline: '2026-08-28 18:00',
    createdAt: '2026-08-25 10:00',
    currentNode: '财务审批',
    currentNodeKey: 'finance',
  },
  {
    id: '5',
    serialNo: 'OA2024-0005',
    title: '会议费用报销',
    initiator: '孙七',
    initiatorDept: '人事部',
    type: '费用报销',
    amount: 8800,
    priority: 'low',
    status: 'pending',
    deadline: '2026-09-10 18:00',
    createdAt: '2026-08-28 16:00',
    currentNode: '部门经理审批',
    currentNodeKey: 'dept_manager',
  },
]

// 获取任务列表
export async function getTasks(status?: string): Promise<Task[]> {
  try {
    const params: Record<string, string> = {}
    if (status && status !== 'all') {
      params.status = status
    }
    const response = await apiClient.get<ApiResponse<Task[]>>('/tasks', { params })
    return response.data.data
  } catch {
    // Mock fallback
    if (status && status !== 'all') {
      return mockTasks.filter((t) => t.status === status)
    }
    return mockTasks
  }
}

// 获取任务详情
export async function getTaskDetail(id: string): Promise<TaskDetail> {
  try {
    const response = await apiClient.get<ApiResponse<TaskDetail>>(`/tasks/${id}`)
    return response.data.data
  } catch {
    // Mock fallback
    const task = mockTasks.find((t) => t.id === id) || mockTasks[0]
    return {
      ...task,
      description: '请领导审批该申请，详情见附件。',
      businessDetails: [
        { id: '1', label: '申请类型', value: task.type },
        { id: '2', label: '申请金额', value: `¥${task.amount.toFixed(2)}` },
        { id: '3', label: '申请事由', value: '因业务需要，申请相关费用' },
        { id: '4', label: '申请日期', value: task.createdAt },
        { id: '5', label: '申请部门', value: task.initiatorDept },
      ],
      attachments: [
        { id: '1', name: '申请单.pdf', size: 256000, type: 'application/pdf', url: '#', uploadedAt: task.createdAt },
        { id: '2', name: '发票照片.jpg', size: 512000, type: 'image/jpeg', url: '#', uploadedAt: task.createdAt },
      ],
      approvalChain: [
        {
          id: '1',
          name: '发起人',
          nodeKey: 'start',
          approver: task.initiator,
          approverId: 'u1',
          status: 'completed',
          action: '提交申请',
          comment: '请领导审批',
          time: task.createdAt,
        },
        {
          id: '2',
          name: '部门经理审批',
          nodeKey: 'dept_manager',
          approver: '部门经理',
          approverId: 'u2',
          status: 'completed',
          action: '同意',
          comment: '情况属实，同意。',
          time: '2026-08-28 11:00',
        },
        {
          id: '3',
          name: task.currentNode,
          nodeKey: task.currentNodeKey,
          approver: '当前处理人',
          approverId: 'u3',
          status: 'current',
        },
        {
          id: '4',
          name: '总经理审批',
          nodeKey: 'general_manager',
          approver: '总经理',
          approverId: 'u4',
          status: 'pending',
        },
      ],
    }
  }
}

// 通过任务
export async function completeTask(id: string, comment: string): Promise<void> {
  try {
    await apiClient.post(`/tasks/${id}/complete`, { comment })
  } catch {
    // Mock: 静默成功
  }
}

// 驳回任务
export async function rejectTask(id: string, comment: string, toNodeKey: string): Promise<void> {
  try {
    await apiClient.post(`/tasks/${id}/reject`, { comment, toNodeKey })
  } catch {
    // Mock: 静默成功
  }
}

// 转办任务
export async function transferTask(id: string, toUserId: string, comment: string): Promise<void> {
  try {
    await apiClient.post(`/tasks/${id}/transfer`, { toUserId, comment })
  } catch {
    // Mock: 静默成功
  }
}
