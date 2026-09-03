import type { ApprovalRecordDTO } from '../types'
import { ActionBadge } from './Badge'
import { formatDateTime } from '../utils/format'

/**
 * 审批历史纵向时间线：
 * SUBMIT / APPROVE 绿、REJECT 红、TRANSFER 紫、CANCEL 灰
 */
export default function ApprovalTimeline({ records }: { records: ApprovalRecordDTO[] }) {
  if (records.length === 0) {
    return <p className="px-1 py-6 text-center text-sm text-slate-400 dark:text-slate-500">暂无审批记录</p>
  }

  return (
    <ol className="relative ml-3 border-l-2 border-slate-200 dark:border-slate-700">
      {records.map((record) => (
        <li key={record.id} className="relative pb-7 pl-6 last:pb-1">
          <span
            className={`absolute -left-[9px] top-0.5 h-4 w-4 rounded-full border-2 border-white dark:border-slate-800 ${timelineDotClass(
              record.action
            )}`}
          />
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-sm font-semibold text-slate-900 dark:text-slate-100">
              {record.nodeName || record.nodeKey}
            </span>
            <ActionBadge action={record.action} />
            <span className="text-xs text-slate-400 dark:text-slate-500">
              {formatDateTime(record.createdAt)}
            </span>
          </div>
          <div className="mt-1 text-sm text-slate-600 dark:text-slate-300">
            <span className="font-medium">{record.operatorName}</span>
            {record.action === 'TRANSFER' && record.toNode ? ` → 转办至 ${record.toNode}` : ''}
            {record.fromNode && record.toNode && record.action !== 'TRANSFER' ? (
              <span className="ml-1 text-xs text-slate-400 dark:text-slate-500">
                （{record.fromNode} → {record.toNode}）
              </span>
            ) : null}
          </div>
          {record.comment && (
            <p className="mt-1.5 rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-600 dark:bg-slate-900/60 dark:text-slate-300">
              {record.comment}
            </p>
          )}
        </li>
      ))}
    </ol>
  )
}

function timelineDotClass(action: ApprovalRecordDTO['action']): string {
  switch (action) {
    case 'SUBMIT':
    case 'APPROVE':
      return 'bg-emerald-500'
    case 'REJECT':
      return 'bg-red-500'
    case 'TRANSFER':
      return 'bg-violet-500'
    case 'CANCEL':
    default:
      return 'bg-slate-400'
  }
}
