// 通用 API 响应结构
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

// 用户
export interface User {
  id: string
  username: string
  nickname: string
  email: string
  phone: string
  department: string
  role: string
  avatar?: string
}

// 登录响应
export interface LoginResult {
  token: string
  refreshToken: string
  user: User
}

// 优先级
export type Priority = 'low' | 'normal' | 'high' | 'urgent'

// 任务状态
export type TaskStatus = 'pending' | 'processing' | 'completed' | 'rejected' | 'overdue'

// 任务
export interface Task {
  id: string
  serialNo: string
  title: string
  initiator: string
  initiatorDept: string
  type: string
  amount: number
  priority: Priority
  status: TaskStatus
  deadline: string
  createdAt: string
  currentNode: string
  currentNodeKey: string
  description?: string
}

// 任务详情
export interface TaskDetail extends Task {
  businessDetails: BusinessDetail[]
  attachments: Attachment[]
  approvalChain: ApprovalNode[]
}

// 业务明细
export interface BusinessDetail {
  id: string
  label: string
  value: string
}

// 附件
export interface Attachment {
  id: string
  name: string
  size: number
  type: string
  url: string
  uploadedAt: string
}

// 审批节点
export interface ApprovalNode {
  id: string
  name: string
  nodeKey: string
  approver: string
  approverId: string
  status: 'completed' | 'current' | 'pending' | 'skipped'
  action?: string
  comment?: string
  time?: string
}

// 审批记录
export interface ApprovalRecord {
  id: string
  step: number
  node: string
  operator: string
  operatorId: string
  action: string
  comment: string
  time: string
}

// 流程定义
export interface ProcessDefinition {
  id: string
  key: string
  name: string
  version: number
  description: string
  deployedAt: string
}

// 流程节点(进度图)
export interface ProcessNode {
  id: string
  name: string
  type: 'start' | 'approval' | 'end'
  assignee?: string
  status: 'completed' | 'current' | 'pending' | 'rejected'
  operator?: string
  action?: string
  comment?: string
  time?: string
}

// 流程实例
export interface ProcessInstance {
  id: string
  serialNo: string
  definitionId: string
  definitionName: string
  businessKey: string
  title: string
  status: 'running' | 'completed' | 'canceled' | 'rejected'
  initiator: string
  initiatorId: string
  currentNode: string
  currentNodeKey: string
  startTime: string
  endTime?: string
  duration?: string
  nodes: ProcessNode[]
  records: ApprovalRecord[]
}

// 公文
export interface Document {
  id: string
  serialNo: string
  title: string
  type: string
  category: string
  status: 'draft' | 'published' | 'archived'
  author: string
  department: string
  content: string
  createdAt: string
  updatedAt: string
}

// 启动流程实例的请求体
export interface StartProcessRequest {
  processDefinitionKey: string
  businessKey: string
  title: string
  variables: Record<string, unknown>
}
