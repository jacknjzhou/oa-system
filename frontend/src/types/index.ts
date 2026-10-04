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

/**
 * 控件类型（P2-1b 动态表单底座，对照致碟云控件库）：
 * 基础：text 单行输入框 / textarea 多行输入框 / richText 富文本（底座以 textarea 渲染）
 * 选择：radio 单选 / checkbox 多选 / select 下拉
 * 文件：image 图片 / attachment 附件
 * 时间：date 日期 / time 时间 / dateRange 日期区间 / datetime 日期时间
 * 关联：contact 联系人 / department 部门
 * 专项：amount 金额 / number 数字 / phone 电话 / idCard 身份证 / provinceCity 省市
 */
export type FormFieldType =
  | 'text'
  | 'textarea'
  | 'richText'
  | 'radio'
  | 'checkbox'
  | 'select'
  | 'image'
  | 'attachment'
  | 'date'
  | 'time'
  | 'dateRange'
  | 'datetime'
  | 'contact'
  | 'department'
  | 'amount'
  | 'number'
  | 'phone'
  | 'idCard'
  | 'provinceCity'

export interface FormField {
  key: string
  label: string
  type: FormFieldType
  required: boolean
  /** 选项（radio/checkbox/select） */
  options?: string[]
  placeholder?: string
  /** 单位后缀（amount 等） */
  unit?: string
  min?: number
  max?: number
}

export interface FormConfig {
  fields: FormField[]
}

// ========== 流程实例 ==========

export type InstanceStatus = 'RUNNING' | 'COMPLETED' | 'CANCELLED' | 'REJECTED' | 'DRAFT'

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
  /** 最近一次非发起审批动作（APPROVE/REJECT/DENY/TRANSFER/CANCEL），用于结果视图 */
  lastAction?: ApprovalAction | null
  lastActionAt?: string | null
  priority: number
  submittedAt: string
  completedAt?: string | null
}

export interface StartInstancePayload {
  defId: string
  title: string
  businessData: string
  /** true = 只存草稿，稍后从“我的申请”提交 */
  draft?: boolean
  /** 抄送人用户 ID：流程完成时抄送通知 */
  ccUserIds?: string[]
  /** 业务类型代码（取审批类型 code） */
  businessType?: string
}

export type ApprovalAction = 'SUBMIT' | 'APPROVE' | 'REJECT' | 'TRANSFER' | 'CANCEL' | 'DENY'

/** 审批类型：发起入口的业务分类（报销/采购/请假…），关联一个流程模板 */
export interface ApprovalType {
  id: string
  code: string
  name: string
  category?: string | null
  icon?: string | null
  description?: string | null
  weight: number
  defId: string
  defName?: string | null
  enabled: boolean
}

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
  countersigns: CountersignNode[]
}

// ========== 会签（多实例并行审批） ==========

export type CountersignElementStatus = 'PENDING' | 'COMPLETED' | 'REJECTED' | 'TERMINATED'

export interface CountersignElement {
  taskId: string
  groupCode: string | null
  groupName: string
  status: CountersignElementStatus
  assignee: string | null
  finishedAt: string | null
}

export interface CountersignNode {
  nodeKey: string
  nodeName: string
  total: number
  completed: number
  rejected: number
  pending: number
  elements: CountersignElement[]
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
  initiatorId?: string | number
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

// ========== 站内通知 ==========

export interface Notification {
  id: number
  title: string
  content: string
  notifyType: 'TASK' | 'PROCESS' | 'DOCUMENT' | 'SYSTEM'
  refType: 'TASK' | 'PROCESS_INSTANCE' | 'DOCUMENT'
  refId: string
  isRead: boolean
  createdAt: string
  readAt?: string | null
}

// ========== 公文 ==========

export type DocType = 'NOTICE' | 'DIRECTIVE' | 'REPORT' | 'LETTER' | 'OTHER'
export type DocumentStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
export type DocUrgency = 'NORMAL' | 'URGENT' | 'CRITICAL'
export type SecrecyLevel = 'PUBLIC' | 'INTERNAL' | 'SECRET' | 'TOP_SECRET'

export interface DocumentDTO {
  id: number
  docNo: string
  title: string
  content: string
  docType: DocType
  urgency: DocUrgency
  secrecyLevel: SecrecyLevel
  authorName: string
  status: DocumentStatus
  publishedAt?: string | null
  archivedAt?: string | null
  createdAt: string
  updatedAt: string
}
