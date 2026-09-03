import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import type { InstanceDTO, InstanceStatus } from '../types'
import { getMyInstances } from '../api/process'
import StatusBadge, { PriorityBadge } from '../components/Badge'
import EmptyState, { ErrorState, LoadingState } from '../components/EmptyState'
import { useToast } from '../components/Toast'
import { formatDateTime } from '../utils/format'

const STATUS_FILTERS: { value: InstanceStatus | 'ALL'; label: string }[] = [
  { value: 'ALL', label: '全部' },
  { value: 'RUNNING', label: '进行中' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'CANCELLED', label: '已取消' },
]

export default function MyInstances() {
  const navigate = useNavigate()
  const { showToast } = useToast()
  const [instances, setInstances] = useState<InstanceDTO[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [statusFilter, setStatusFilter] = useState<InstanceStatus | 'ALL'>('ALL')

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setLoadError('')
    getMyInstances()
      .then((data) => {
        if (!cancelled) setInstances(data)
      })
      .catch((err) => {
        if (!cancelled) {
          const message = err instanceof Error ? err.message : '申请列表加载失败'
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

  const visibleInstances = useMemo(
    () => (statusFilter === 'ALL' ? instances : instances.filter((i) => i.status === statusFilter)),
    [instances, statusFilter]
  )

  return (
    <div className="mx-auto max-w-5xl p-6">
      <div className="mb-6">
        <h2 className="text-xl font-bold text-slate-900 dark:text-slate-100">我的申请</h2>
        <p className="mt-0.5 text-sm text-slate-500 dark:text-slate-400">查看我发起的流程实例及进展</p>
      </div>

      {/* 状态筛选 */}
      <div className="mb-6 flex flex-wrap gap-2">
        {STATUS_FILTERS.map((filter) => (
          <button
            key={filter.value}
            type="button"
            className={`rounded-full px-4 py-1.5 text-sm font-medium transition-colors ${
              statusFilter === filter.value
                ? 'bg-primary-600 text-white shadow-sm'
                : 'bg-white text-slate-600 ring-1 ring-inset ring-slate-200 hover:bg-slate-50 dark:bg-slate-800 dark:text-slate-300 dark:ring-slate-600 dark:hover:bg-slate-700'
            }`}
            onClick={() => setStatusFilter(filter.value)}
          >
            {filter.label}
          </button>
        ))}
      </div>

      {loading ? (
        <LoadingState text="正在加载申请列表…" />
      ) : loadError ? (
        <div className="card">
          <ErrorState message={loadError} />
        </div>
      ) : visibleInstances.length === 0 ? (
        <div className="card">
          <EmptyState
            icon="📄"
            title="暂无申请记录"
            description="从「发起审批」页面提交你的第一个申请"
          />
        </div>
      ) : (
        <div className="space-y-3">
          {visibleInstances.map((instance) => (
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
                </div>
                <p className="mt-1 flex flex-wrap gap-x-4 gap-y-0.5 text-xs text-slate-500 dark:text-slate-400">
                  <span>单号：{instance.instanceNo}</span>
                  <span>模板：{instance.defName} v{instance.defVersion}</span>
                  <span>
                    节点：
                    {instance.status === 'RUNNING'
                      ? instance.currentNodeName || instance.currentNode || '-'
                      : instance.status === 'COMPLETED'
                        ? '已结束'
                        : '-'}
                  </span>
                </p>
              </div>
              <div className="shrink-0 text-right">
                <p className="text-xs text-slate-400 dark:text-slate-500">
                  提交于 {formatDateTime(instance.submittedAt)}
                </p>
                <span className="mt-1 inline-flex items-center gap-1 text-sm font-medium text-primary-600 dark:text-primary-400">
                  跟踪详情
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
