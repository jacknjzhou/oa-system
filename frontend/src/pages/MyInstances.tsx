import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import type { ApprovalType, InstanceDTO } from '../types'
import { getMyInstances, submitInstance, withdrawInstance } from '../api/process'
import { getApprovalTypes } from '../api/approvalType'
import StatusBadge, { PriorityBadge } from '../components/Badge'
import EmptyState, { ErrorState, LoadingState } from '../components/EmptyState'
import { useToast } from '../components/Toast'
import { formatDateTime } from '../utils/format'

/**
 * 结果视图 Tab（对照致碟云“审批中心”）：
 * 审批中 / 已同意 / 已拒绝（整单终止）/ 已驳回（打回节点等待中）/ 已撤回 / 草稿。
 */
const STATUS_FILTERS: { value: string; label: string; match: (i: InstanceDTO) => boolean }[] = [
  { value: 'ALL', label: '全部', match: () => true },
  { value: 'RUNNING', label: '审批中', match: (i) => i.status === 'RUNNING' },
  { value: 'APPROVED', label: '已同意', match: (i) => i.status === 'COMPLETED' },
  { value: 'DENIED', label: '已拒绝', match: (i) => i.status === 'REJECTED' },
  { value: 'SENT_BACK', label: '已驳回', match: (i) => i.status === 'RUNNING' && i.lastAction === 'REJECT' },
  { value: 'WITHDRAWN', label: '已撤回', match: (i) => i.status === 'CANCELLED' },
  { value: 'DRAFT', label: '草稿', match: (i) => i.status === 'DRAFT' },
]

export default function MyInstances() {
  const navigate = useNavigate()
  const { showToast } = useToast()
  const [instances, setInstances] = useState<InstanceDTO[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [statusFilter, setStatusFilter] = useState<string>('ALL')
  const [submittingId, setSubmittingId] = useState<string | null>(null)
  const [withdrawingId, setWithdrawingId] = useState<string | null>(null)
  const [types, setTypes] = useState<ApprovalType[]>([])

  useEffect(() => {
    // 类型名称解析（code → name）；失败时回退显示 code
    getApprovalTypes()
      .then(setTypes)
      .catch(() => setTypes([]))
  }, [])

  const typeName = (code?: string) => {
    if (!code) return ''
    return types.find((t) => t.code === code)?.name ?? code
  }

  const loadInstances = useCallback(() => {
    setLoading(true)
    setLoadError('')
    getMyInstances()
      .then(setInstances)
      .catch((err) => {
        const message = err instanceof Error ? err.message : '申请列表加载失败'
        setLoadError(message)
        showToast(message, 'error')
      })
      .finally(() => setLoading(false))
  }, [showToast])

  useEffect(() => {
    loadInstances()
  }, [loadInstances])

  const handleSubmitDraft = async (id: string) => {
    setSubmittingId(id)
    try {
      await submitInstance(id)
      showToast('草稿已提交，流程开始审批', 'success')
      loadInstances()
    } catch (err) {
      showToast(err instanceof Error ? err.message : '提交失败', 'error')
    } finally {
      setSubmittingId(null)
    }
  }

  const handleWithdraw = async (id: string, title: string) => {
    if (!window.confirm(`确定撤回流程「${title}」？撤回后审批终止。`)) return
    setWithdrawingId(id)
    try {
      await withdrawInstance(id)
      showToast('流程已撤回', 'success')
      loadInstances()
    } catch (err) {
      showToast(err instanceof Error ? err.message : '撤回失败', 'error')
    } finally {
      setWithdrawingId(null)
    }
  }

  const visibleInstances = useMemo(() => {
    const filter = STATUS_FILTERS.find((f) => f.value === statusFilter) ?? STATUS_FILTERS[0]
    return instances.filter(filter.match)
  }, [instances, statusFilter])

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
                  {instance.businessType && (
                    <span className="rounded bg-slate-100 px-1.5 py-0.5 text-slate-500 dark:bg-slate-700 dark:text-slate-300">
                      {typeName(instance.businessType)}
                    </span>
                  )}
                  <span>
                    节点：
                    {instance.status === 'RUNNING'
                      ? instance.currentNodeName || instance.currentNode || '-'
                      : instance.status === 'COMPLETED'
                        ? '已结束'
                        : instance.status === 'DRAFT'
                          ? '未提交'
                          : '-'}
                  </span>
                </p>
              </div>
              <div className="shrink-0 text-right">
                <p className="text-xs text-slate-400 dark:text-slate-500">
                  {instance.status === 'DRAFT' ? '草稿' : `提交于 ${formatDateTime(instance.submittedAt)}`}
                </p>
                {instance.status === 'DRAFT' ? (
                  <span
                    role="button"
                    tabIndex={0}
                    className="mt-1 inline-flex cursor-pointer items-center gap-1 text-sm font-medium text-amber-600 hover:underline dark:text-amber-400"
                    onClick={(e) => {
                      e.stopPropagation()
                      handleSubmitDraft(instance.id)
                    }}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        e.stopPropagation()
                        handleSubmitDraft(instance.id)
                      }
                    }}
                  >
                    {submittingId === instance.id ? '提交中…' : '提交草稿'}
                  </span>
                ) : (
                  <div className="mt-1 flex flex-wrap items-center justify-end gap-2">
                    {(instance.status === 'RUNNING' || instance.status === 'REJECTED') && (
                      <span
                        role="button"
                        tabIndex={0}
                        className="inline-flex cursor-pointer items-center gap-1 rounded-full border border-rose-300 bg-rose-50 px-2.5 py-0.5 text-xs font-medium text-rose-700 hover:bg-rose-100 dark:border-rose-500/50 dark:bg-rose-500/10 dark:text-rose-400"
                        onClick={(e) => {
                          e.stopPropagation()
                          handleWithdraw(instance.id, instance.title)
                        }}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') {
                            e.stopPropagation()
                            handleWithdraw(instance.id, instance.title)
                          }
                        }}
                      >
                        {withdrawingId === instance.id ? '撤回中…' : '撤回'}
                      </span>
                    )}
                    <span className="inline-flex items-center gap-1 text-sm font-medium text-primary-600 dark:text-primary-400">
                      跟踪详情
                      <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                        <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
                      </svg>
                    </span>
                  </div>
                )}
              </div>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
