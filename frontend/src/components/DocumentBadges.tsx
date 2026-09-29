import type { DocType, DocUrgency, DocumentStatus, SecrecyLevel } from '../types'

export const DOC_TYPE_LABELS: Record<DocType, string> = {
  NOTICE: '通知',
  DIRECTIVE: '指令',
  REPORT: '报告',
  LETTER: '函件',
  OTHER: '其他',
}

export const URGENCY_LABELS: Record<DocUrgency, string> = {
  NORMAL: '普通',
  URGENT: '紧急',
  CRITICAL: '特急',
}

export const SECRECY_LABELS: Record<SecrecyLevel, string> = {
  PUBLIC: '公开',
  INTERNAL: '内部',
  SECRET: '机密',
  TOP_SECRET: '绝密',
}

export const DOC_STATUS_LABELS: Record<DocumentStatus, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  ARCHIVED: '已归档',
}

const STATUS_CLS: Record<DocumentStatus, string> = {
  DRAFT: 'bg-slate-100 text-slate-600 ring-slate-500/20 dark:bg-slate-700 dark:text-slate-300 dark:ring-slate-400/20',
  PUBLISHED: 'bg-emerald-100 text-emerald-700 ring-emerald-600/20 dark:bg-emerald-500/15 dark:text-emerald-400 dark:ring-emerald-500/30',
  ARCHIVED: 'bg-blue-100 text-blue-700 ring-blue-600/20 dark:bg-blue-500/15 dark:text-blue-400 dark:ring-blue-500/30',
}

export function DocStatusBadge({ status }: { status: DocumentStatus }) {
  return (
    <span
      className={`inline-flex shrink-0 items-center rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${
        STATUS_CLS[status] ?? STATUS_CLS.DRAFT
      }`}
    >
      {DOC_STATUS_LABELS[status] ?? status}
    </span>
  )
}

const URGENCY_CLS: Record<DocUrgency, string> = {
  NORMAL: 'bg-slate-100 text-slate-600 ring-slate-500/20 dark:bg-slate-700 dark:text-slate-300 dark:ring-slate-400/20',
  URGENT: 'bg-amber-100 text-amber-700 ring-amber-600/20 dark:bg-amber-500/15 dark:text-amber-400 dark:ring-amber-500/30',
  CRITICAL: 'bg-red-100 text-red-700 ring-red-600/20 dark:bg-red-500/15 dark:text-red-400 dark:ring-red-500/30',
}

export function UrgencyBadge({ urgency }: { urgency: DocUrgency }) {
  return (
    <span
      className={`inline-flex shrink-0 items-center rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${
        URGENCY_CLS[urgency] ?? URGENCY_CLS.NORMAL
      }`}
    >
      {URGENCY_LABELS[urgency] ?? urgency}
    </span>
  )
}

const SECRECY_CLS: Record<SecrecyLevel, string> = {
  PUBLIC: 'bg-slate-100 text-slate-600 ring-slate-500/20 dark:bg-slate-700 dark:text-slate-300 dark:ring-slate-400/20',
  INTERNAL: 'bg-sky-100 text-sky-700 ring-sky-600/20 dark:bg-sky-500/15 dark:text-sky-400 dark:ring-sky-500/30',
  SECRET: 'bg-violet-100 text-violet-700 ring-violet-600/20 dark:bg-violet-500/15 dark:text-violet-400 dark:ring-violet-500/30',
  TOP_SECRET: 'bg-red-100 text-red-700 ring-red-600/20 dark:bg-red-500/15 dark:text-red-400 dark:ring-red-500/30',
}

export function SecrecyBadge({ level }: { level: SecrecyLevel }) {
  return (
    <span
      className={`inline-flex shrink-0 items-center rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset ${
        SECRECY_CLS[level] ?? SECRECY_CLS.PUBLIC
      }`}
    >
      {SECRECY_LABELS[level] ?? level}
    </span>
  )
}
