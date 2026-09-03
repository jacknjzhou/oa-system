import type { ApprovalAction, InstanceStatus, TaskStatus, TemplateStatus } from '../types'

type BadgeStyle = { label: string; cls: string }

const STATUS_MAP: Record<string, BadgeStyle> = {
  // 任务状态
  PENDING: {
    label: '待审批',
    cls: 'bg-amber-100 text-amber-700 ring-amber-600/20 dark:bg-amber-500/15 dark:text-amber-400 dark:ring-amber-500/30',
  },
  COMPLETED: {
    label: '已完成',
    cls: 'bg-emerald-100 text-emerald-700 ring-emerald-600/20 dark:bg-emerald-500/15 dark:text-emerald-400 dark:ring-emerald-500/30',
  },
  REJECTED: {
    label: '已驳回',
    cls: 'bg-red-100 text-red-700 ring-red-600/20 dark:bg-red-500/15 dark:text-red-400 dark:ring-red-500/30',
  },
  // 实例状态
  RUNNING: {
    label: '进行中',
    cls: 'bg-blue-100 text-blue-700 ring-blue-600/20 dark:bg-blue-500/15 dark:text-blue-400 dark:ring-blue-500/30',
  },
  CANCELLED: {
    label: '已取消',
    cls: 'bg-slate-200 text-slate-600 ring-slate-500/20 dark:bg-slate-500/15 dark:text-slate-300 dark:ring-slate-400/20',
  },
  // 模板状态
  DRAFT: {
    label: '草稿',
    cls: 'bg-slate-100 text-slate-600 ring-slate-500/20 dark:bg-slate-700 dark:text-slate-300 dark:ring-slate-400/20',
  },
  PUBLISHED: {
    label: '已发布',
    cls: 'bg-emerald-100 text-emerald-700 ring-emerald-600/20 dark:bg-emerald-500/15 dark:text-emerald-400 dark:ring-emerald-500/30',
  },
  DISABLED: {
    label: '已停用',
    cls: 'bg-red-100 text-red-700 ring-red-600/20 dark:bg-red-500/15 dark:text-red-400 dark:ring-red-500/30',
  },
}

const FALLBACK: BadgeStyle = {
  label: '未知',
  cls: 'bg-slate-100 text-slate-600 ring-slate-500/20 dark:bg-slate-700 dark:text-slate-300 dark:ring-slate-400/20',
}

export type BadgeStatus =
  | TaskStatus
  | InstanceStatus
  | TemplateStatus
  | 'PENDING'
  | 'COMPLETED'
  | 'REJECTED'
  | 'RUNNING'
  | 'CANCELLED'
  | 'DRAFT'
  | 'PUBLISHED'
  | 'DISABLED'

export default function StatusBadge({ status }: { status: BadgeStatus }) {
  const style = STATUS_MAP[status] ?? FALLBACK
  return (
    <span
      className={`inline-flex shrink-0 items-center rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${style.cls}`}
    >
      {style.label}
    </span>
  )
}

const PRIORITY_MAP: BadgeStyle[] = [
  { label: '普通', cls: 'bg-slate-100 text-slate-600 ring-slate-500/20 dark:bg-slate-700 dark:text-slate-300 dark:ring-slate-400/20' },
  { label: '重要', cls: 'bg-amber-100 text-amber-700 ring-amber-600/20 dark:bg-amber-500/15 dark:text-amber-400 dark:ring-amber-500/30' },
  { label: '紧急', cls: 'bg-red-100 text-red-700 ring-red-600/20 dark:bg-red-500/15 dark:text-red-400 dark:ring-red-500/30' },
]

export function PriorityBadge({ priority }: { priority: number }) {
  const level = priority >= 2 ? 2 : priority >= 1 ? 1 : 0
  const style = PRIORITY_MAP[level]
  return (
    <span
      className={`inline-flex shrink-0 items-center rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${style.cls}`}
    >
      {style.label}
    </span>
  )
}

const ACTION_MAP: Record<ApprovalAction, BadgeStyle> = {
  SUBMIT: {
    label: '提交',
    cls: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-400',
  },
  APPROVE: {
    label: '通过',
    cls: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-400',
  },
  REJECT: {
    label: '驳回',
    cls: 'bg-red-100 text-red-700 dark:bg-red-500/15 dark:text-red-400',
  },
  TRANSFER: {
    label: '转办',
    cls: 'bg-violet-100 text-violet-700 dark:bg-violet-500/15 dark:text-violet-400',
  },
  CANCEL: {
    label: '取消',
    cls: 'bg-slate-200 text-slate-600 dark:bg-slate-700 dark:text-slate-300',
  },
}

export function ActionBadge({ action }: { action: ApprovalAction }) {
  const style = ACTION_MAP[action] ?? { label: action, cls: ACTION_MAP.CANCEL.cls }
  return (
    <span className={`inline-flex shrink-0 items-center rounded-md px-2 py-0.5 text-xs font-medium ${style.cls}`}>
      {style.label}
    </span>
  )
}
