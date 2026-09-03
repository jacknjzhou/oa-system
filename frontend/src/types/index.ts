// ========== 通用 ==========

export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

// ========== 认证 / 用户 ==========

export interface UserInfo {
  id: string
  username: string
  realName: string
  email: string
  phone: string
  position: string
  roles: string[]
}

export interface LoginResult {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  userInfo: UserInfo
}

export interface UserSummary {
  id: string
  username: string
  realName: string
  position: string
  roles: string[]
}

export interface Role {
  id: string
  code: string
  name: string
}

// ========== 审批模板 ==========

export type TemplateStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED'

export interface Template {
  id: string
  defKey: string
  name: string
  version: number
  category: string
  status: TemplateStatus
  formConfig: string
  /** 仅 GET /api/process-definitions/{id} 单查时返回 */
  bpmnXml?: string
  publishedAt?: string | null
}

export interface TemplateCreatePayload {
  defKey: string
  name: string
  category: string
  formConfig: string
  bpmnXml: string
}

export interface TemplateUpdatePayload {
  name: string
  category: string
  formConfig: string
  bpmnXml: string
}

// ========== 表单配置 ==========

export type FormFieldType = 'number' | 'text' | 'textarea' | 'select'

export interface FormField {
  key: string
  label: string
  type: FormFieldType
  required: boolean
  options?: string[]
}

export interface FormConfig {
  fields: FormField[]
}

// ========== 流程实例 ==========

export type InstanceStatus = 'RUNNING' | 'COMPLETED' | 'CANCELLED' | 'REJECTED'

export interface InstanceDTO {
  id: string
  instanceNo: string
  defId: string
  defKey: string
  defName: string
  defVersion: number
  title: string
  initiatorId: string
  initiatorName: string
  businessType: string
  businessData: string
  currentNode: string
  currentNodeName: string
  status: InstanceStatus
  priority: number
  submittedAt: string
  completedAt?: string | null
}

export interface StartInstancePayload {
  defId: string
  title: string
  businessData: string
}

export type ApprovalAction = 'SUBMIT' | 'APPROVE' | 'REJECT' | 'TRANSFER' | 'CANCEL'

export interface ApprovalRecordDTO {
  id: string
  taskId: string
  nodeKey: string
  nodeName: string
  action: ApprovalAction
  operatorName: string
  comment?: string | null
  fromNode?: string | null
  toNode?: string | null
  createdAt: string
}

export interface InstanceDetail {
  instance: InstanceDTO
  bpmnXml: string
  completedActivityIds: string[]
  currentActivityIds: string[]
  approvalRecords: ApprovalRecordDTO[]
}

// ========== 审批任务 ==========

export type TaskStatus = 'PENDING' | 'COMPLETED' | 'REJECTED'

export interface TaskDTO {
  id: string
  instanceId: string
  title: string
  defKey: string
  defName: string
  nodeKey: string
  nodeName: string
  assignee: string
  candidateRoles: string[]
  createTime: string
  endTime?: string | null
  priority: number
  initiatorName: string
  status: TaskStatus
  comment?: string | null
}

export interface TaskDetail {
  task: TaskDTO
  instance: InstanceDTO
  bpmnXml: string
  completedActivityIds: string[]
  currentActivityIds: string[]
  approvalRecords: ApprovalRecordDTO[]
}
