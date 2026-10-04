import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import type { TaskDTO } from '../types'
import { getDoneTasks, getTodoTasks, remindTask } from '../api/task'
import { withdrawInstance } from '../api/process'
import { getStoredUser } from '../api/auth'
import StatusBadge, { PriorityBadge } from '../components/Badge'
import EmptyState, { ErrorState, LoadingState } from '../components/EmptyState'
import { useToast } from '../components/Toast'
import { formatDateTime } from '../utils/format'

interface TaskListProps {
  mode: 'todo' | 'done'
}

export default function TaskList({ mode }: TaskListProps) {
  const navigate = useNavigate()
  const { showToast } = useToast()
  const user = getStoredUser()
  const [tasks, setTasks] = useState<TaskDTO[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [acting, setActing] = useState(false)

  const loadTasks = useCallback(() => {
    setLoading(true)
    setLoadError('')
    const request = mode === 'todo' ? getTodoTasks() : getDoneTasks()
    request
      .then((data) => setTasks(data))
      .catch((err) => {
        const message = err instanceof Error ? err.message : '任务加载失败'
        setLoadError(message)
        showToast(message, 'error')
      })
      .finally(() => setLoading(false))
  }, [mode, showToast])

  useEffect(() => {
    loadTasks()
  }, [loadTasks])

  /** 仅发起人可见催办/撤回 */
  const isInitiator = (task: TaskDTO) =>
    !!user && task.initiatorId != null && String(user.id) === String(task.initiatorId)

  const handleRemind = async (e: React.MouseEvent, task: TaskDTO) => {
    e.stopPropagation()
    if (acting) return
    setActing(true)
    try {
      await remindTask(task.id)
      showToast('已提醒审批人尽快处理', 'success')
    } catch (err) {
      showToast(err instanceof Error ? err.message : '催办失败', 'error')
    } finally {
      setActing(false)
    }
  }

  const handleWithdraw = async (e: React.MouseEvent, task: TaskDTO) => {
    e.stopPropagation()
    if (acting) return
    if (!window.confirm(`确定撤回流程「${task.title}」？撤回后审批终止。`)) return
    setActing(true)
    try {
      await withdrawInstance(task.instanceId)
      showToast('流程已撤回', 'success')
      loadTasks()
    } catch (err) {
      showToast(err instanceof Error ? err.message : '撤回失败', 'error')
    } finally {
      setActing(false)
    }
  }

  return (
    <div className="mx-auto max-w-5xl p-6">
      <div className="mb-6">
        <h2 className="text-xl font-bold text-slate-900 dark:text-slate-100">
          {mode === 'todo' ? '待审批' : '已审批'}
        </h2>
        <p className="mt-0.5 text-sm text-slate-500 dark:text-slate-400">
          {mode === 'todo' ? '等待你处理的审批任务' : '你已办结的历史审批任务'}
        </p>
      </div>

      {loading ? (
        <LoadingState text="正在加载任务…" />
      ) : loadError ? (
        <div className="card">
          <ErrorState message={loadError} />
        </div>
      ) : tasks.length === 0 ? (
        <div className="card">
          <EmptyState
            icon={mode === 'todo' ? '🎉' : '📚'}
            title={mode === 'todo' ? '暂无待审批任务' : '暂无已办记录'}
            description={
              mode === 'todo' ? '太棒了，所有任务都已处理完毕' : '处理完成的任务将在这里留痕'
            }
          />
        </div>
      ) : (
        <div className="space-y-3">
          {tasks.map((task) => (
            <button
              key={task.id}
              type="button"
              className="card flex w-full items-center gap-4 p-4 text-left transition-all hover:border-primary-300 hover:shadow-lifted dark:hover:border-primary-500/50"
              onClick={() => navigate(`/task/${task.id}`)}
            >
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <h3 className="truncate text-sm font-semibold text-slate-900 dark:text-slate-100">
                    {task.title}
                  </h3>
                  <PriorityBadge priority={task.priority} />
                  {mode === 'done' && <StatusBadge status={task.status} />}
                </div>
                <p className="mt-1 flex flex-wrap gap-x-4 gap-y-0.5 text-xs text-slate-500 dark:text-slate-400">
                  <span>发起人：{task.initiatorName}</span>
                  <span>模板：{task.defName}</span>
                  <span>当前节点：{task.nodeName}</span>
                </p>
              </div>
              <div className="shrink-0 text-right">
                <p className="text-xs text-slate-400 dark:text-slate-500">
                  {mode === 'todo' ? `创建于 ${formatDateTime(task.createTime)}` : `办结于 ${formatDateTime(task.endTime)}`}
                </p>
                <span className="mt-1 inline-flex items-center gap-1 text-sm font-medium text-primary-600 dark:text-primary-400">
                  {mode === 'todo' ? '去审批' : '查看'}
                  <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
                  </svg>
                </span>
                {mode === 'todo' && isInitiator(task) && (
                  <div className="mt-2 flex justify-end gap-2">
                    <span
                      role="button"
                      className="inline-flex items-center gap-1 rounded-full border border-amber-300 bg-amber-50 px-2.5 py-0.5 text-xs font-medium text-amber-700 hover:bg-amber-100 dark:border-amber-500/50 dark:bg-amber-500/10 dark:text-amber-400"
                      onClick={(e) => handleRemind(e, task)}
                    >
                      🔔 催办
                    </span>
                    <span
                      role="button"
                      className="inline-flex items-center gap-1 rounded-full border border-rose-300 bg-rose-50 px-2.5 py-0.5 text-xs font-medium text-rose-700 hover:bg-rose-100 dark:border-rose-500/50 dark:bg-rose-500/10 dark:text-rose-400"
                      onClick={(e) => handleWithdraw(e, task)}
                    >
                      撤回
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
