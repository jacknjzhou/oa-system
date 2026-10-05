import type { FieldPerm, FormField, InstanceDTO } from '../types'
import StatusBadge, { PriorityBadge } from './Badge'
import { formatDateTime, parseBusinessData } from '../utils/format'

interface InstanceInfoCardProps {
  instance: InstanceDTO
  /** 模板表单字段配置（用于将 businessData 按键值展示），null 表示未能获取 */
  formFields: FormField[] | null
  /** 当前节点的字段级权限（P2-3）：hidden 字段不展示 */
  perms?: Record<string, FieldPerm>
}

/**
 * 流程实例信息卡：标题 / 发起人 / 状态 / 业务数据（按 formConfig 键值展示）
 */
export default function InstanceInfoCard({ instance, formFields, perms }: InstanceInfoCardProps) {
  const businessData = parseBusinessData(instance.businessData)
  const dataEntries = Object.entries(businessData).filter(([key]) => perms?.[key] !== 'hidden')

  return (
    <div className="card p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h2 className="text-lg font-semibold text-slate-900 dark:text-slate-100">{instance.title}</h2>
            <StatusBadge status={instance.status} />
            <PriorityBadge priority={instance.priority} />
          </div>
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
            单号 {instance.instanceNo} · {instance.defName} v{instance.defVersion}（{instance.defKey}）
          </p>
        </div>
      </div>

      <dl className="mt-4 grid grid-cols-1 gap-x-6 gap-y-3 border-t border-slate-100 pt-4 text-sm dark:border-slate-700 sm:grid-cols-2 lg:grid-cols-3">
        <div>
          <dt className="text-slate-400 dark:text-slate-500">发起人</dt>
          <dd className="mt-0.5 font-medium text-slate-800 dark:text-slate-200">
            {instance.initiatorName}
            {instance.initiatorOrgName && (
              <span className="ml-2 text-xs font-normal text-slate-400">{instance.initiatorOrgName}</span>
            )}
            {instance.initiatorPosition && (
              <span className="ml-2 text-xs font-normal text-slate-400">{instance.initiatorPosition}</span>
            )}
          </dd>
        </div>
        <div>
          <dt className="text-slate-400 dark:text-slate-500">提交时间</dt>
          <dd className="mt-0.5 font-medium text-slate-800 dark:text-slate-200">
            {formatDateTime(instance.submittedAt)}
          </dd>
        </div>
        <div>
          <dt className="text-slate-400 dark:text-slate-500">当前节点</dt>
          <dd className="mt-0.5 font-medium text-slate-800 dark:text-slate-200">
            {instance.status === 'RUNNING' ? instance.currentNodeName || instance.currentNode || '-' : '-'}
          </dd>
        </div>
        {instance.completedAt && (
          <div>
            <dt className="text-slate-400 dark:text-slate-500">完成时间</dt>
            <dd className="mt-0.5 font-medium text-slate-800 dark:text-slate-200">
              {formatDateTime(instance.completedAt)}
            </dd>
          </div>
        )}
      </dl>

      <div className="mt-4 border-t border-slate-100 pt-4 dark:border-slate-700">
        <h3 className="mb-2 text-sm font-semibold text-slate-700 dark:text-slate-300">业务数据</h3>
        {dataEntries.length === 0 ? (
          <p className="text-sm text-slate-400 dark:text-slate-500">暂无业务数据</p>
        ) : (
          <dl className="grid grid-cols-1 gap-x-6 gap-y-2.5 text-sm sm:grid-cols-2 lg:grid-cols-3">
            {dataEntries.map(([key, value]) => {
              const field = formFields?.find((f) => f.key === key)
              return (
                <div key={key} className="flex min-w-0 items-baseline gap-2">
                  <dt className="shrink-0 text-slate-400 dark:text-slate-500">{field?.label || key}：</dt>
                  <dd className="min-w-0 break-all font-medium text-slate-800 dark:text-slate-200">
                    {formatValue(value)}
                  </dd>
                </div>
              )
            })}
          </dl>
        )}
      </div>
    </div>
  )
}

function formatValue(value: unknown): string {
  if (value === null || value === undefined) return '-'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}
