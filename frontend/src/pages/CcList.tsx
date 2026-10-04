import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import type { InstanceDTO } from '../types'
import { getCcInstances } from '../api/process'
import StatusBadge, { PriorityBadge } from '../components/Badge'
import EmptyState, { ErrorState, LoadingState } from '../components/EmptyState'
import { useToast } from '../components/Toast'
import { formatDateTime } from '../utils/format'

/**
 * 抄送给我：我被抄送的流程实例（只读）。
 * 每实例每人只抄送一次（后端 cc_record 唯一约束去重），故列表天然无重复。
 */
export default function CcList() {
  const navigate = useNavigate()
  const { showToast } = useToast()
  const [instances, setInstances] = useState<InstanceDTO[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setLoadError('')
    getCcInstances()
      .then((data) => {
        if (!cancelled) setInstances(data)
      })
      .catch((err) => {
        if (!cancelled) {
          const message = err instanceof Error ? err.message : '抄送列表加载失败'
          setLoadError(message)
          showToast(message, 'error')
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [showToast])

  const sorted = useMemo(
    () =>
      [...instances].sort((a, b) =>
        String(b.submittedAt || b.lastActionAt || '').localeCompare(String(a.submittedAt || a.lastActionAt || ''))
      ),
    [instances]
  )

  return (
    <div className="mx-auto max-w-5xl p-6">
      <div className="mb-6">
        <h2 className="text-xl font-bold text-slate-900 dark:text-slate-100">抄送给我</h2>
        <p className="mt-0.5 text-sm text-slate-500 dark:text-slate-400">流程抄送给我的实例，仅供查阅，无需审批</p>
      </div>

      {loading ? (
        <LoadingState text="正在加载抄送列表…" />
      ) : loadError ? (
        <div className="card">
          <ErrorState message={loadError} />
        </div>
      ) : sorted.length === 0 ? (
        <div className="card">
          <EmptyState
            icon="📮"
            title="暂无抄送"
            description="当有人发起流程并抄送给你时，会在这里出现"
          />
        </div>
      ) : (
        <div className="space-y-3">
          {sorted.map((instance) => (
            <button
              key={instance.id}
              type="button"
              className="card flex w-full items-center gap-4 p-4 text-left transition-all hover:border-primary-300 hover:shadow-lifted dark:hover:border-primary-500/50"
              onClick={() => navigate(`/tracking/${instance.id}`)}
            >
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <h3 className="truncate text-sm font-semibold text-slate-900 dark:text-slate-100">
                    {instance.title}
                  </h3>
                  <StatusBadge status={instance.status} />
                  <PriorityBadge priority={instance.priority} />
                  <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-500 dark:bg-slate-700 dark:text-slate-300">
                    抄送
                  </span>
                </div>
                <p className="mt-1 flex flex-wrap gap-x-4 gap-y-0.5 text-xs text-slate-500 dark:text-slate-400">
                  <span>单号：{instance.instanceNo}</span>
                  <span>发起人：{instance.initiatorName}</span>
                  <span>模板：{instance.defName} v{instance.defVersion}</span>
                </p>
              </div>
              <div className="shrink-0 text-right">
                <p className="text-xs text-slate-400 dark:text-slate-500">
                  提交于 {formatDateTime(instance.submittedAt)}
                </p>
                <span className="mt-1 inline-flex items-center gap-1 text-sm font-medium text-primary-600 dark:text-primary-400">
                  查看详情
                  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
                  </svg>
                </span>
              </div>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
