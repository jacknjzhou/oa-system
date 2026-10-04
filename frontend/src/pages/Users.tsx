import { useCallback, useEffect, useMemo, useState } from 'react'
import { userApi, type UserRow } from '../api/user'
import EmptyState, { ErrorState, LoadingState } from '../components/EmptyState'
import { useToast } from '../components/Toast'
import { getStoredUser } from '../api/auth'
import { formatDateTime } from '../utils/format'

const STATUS_META: Record<UserRow['status'], { label: string; cls: string }> = {
  ACTIVE: { label: '正常', cls: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300' },
  INACTIVE: { label: '已禁用', cls: 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300' },
  LOCKED: { label: '已锁定', cls: 'bg-rose-100 text-rose-700 dark:bg-rose-900/40 dark:text-rose-300' },
  DELETED: { label: '已删除', cls: 'bg-slate-200 text-slate-500 dark:bg-slate-700 dark:text-slate-400' },
}

/** 员工管理（SY-01）：正常/回收站 + 禁用、删除、恢复。 */
export default function Users() {
  const { showToast } = useToast()
  const me = getStoredUser()
  const [tab, setTab] = useState<'active' | 'recycle'>('active')
  const [rows, setRows] = useState<UserRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(0)

  const load = useCallback(() => {
    setLoading(true)
    setError('')
    userApi
      .list(tab === 'recycle')
      .then(setRows)
      .catch((err) => setError(err instanceof Error ? err.message : '加载失败'))
      .finally(() => setLoading(false))
  }, [tab])

  useEffect(() => {
    load()
  }, [load])

  const act = async (id: number, fn: () => Promise<unknown>, msg: string) => {
    if (busy) return
    setBusy(id)
    try {
      await fn()
      showToast(msg, 'success')
      load()
    } catch (err) {
      showToast(err instanceof Error ? err.message : '操作失败', 'error')
    } finally {
      setBusy(0)
    }
  }

  const deletedCount = useMemo(() => rows.filter((r) => r.status === 'DELETED').length, [rows])

  if (loading && rows.length === 0) return <LoadingState />
  if (error && rows.length === 0) return <ErrorState message={error} onRetry={load} />

  return (
    <div className="mx-auto max-w-4xl space-y-4">
      <div className="flex gap-1 rounded-xl bg-slate-100 p-1 dark:bg-slate-800 w-fit">
        {(['active', 'recycle'] as const).map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`rounded-lg px-4 py-1.5 text-sm transition ${
              tab === t
                ? 'bg-white font-medium shadow-sm dark:bg-slate-700'
                : 'text-slate-500 hover:text-slate-700 dark:hover:text-slate-300'
            }`}
          >
            {t === 'active' ? `成员` : `回收站 (${deletedCount})`}
          </button>
        ))}
      </div>

      <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-800">
        {rows.length === 0 ? (
          <div className="p-8">
            <EmptyState
              title={tab === 'active' ? '暂无成员' : '回收站为空'}
              description={tab === 'active' ? '系统用户将显示在这里' : '删除的用户会进入回收站，可恢复'}
            />
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-100 text-left text-xs text-slate-400 dark:border-slate-700">
                <th className="px-4 py-3 font-medium">账号</th>
                <th className="px-4 py-3 font-medium">姓名</th>
                <th className="px-4 py-3 font-medium">角色</th>
                <th className="px-4 py-3 font-medium">状态</th>
                <th className="px-4 py-3 font-medium">操作</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => {
                const meta = STATUS_META[r.status] || STATUS_META.INACTIVE
                const isSelf = me?.username === r.username
                return (
                  <tr key={r.id} className="border-b border-slate-50 last:border-0 dark:border-slate-700/50">
                    <td className="px-4 py-3 font-mono text-xs">{r.username}</td>
                    <td className="px-4 py-3">
                      {r.realName || '—'}
                      {r.position ? <span className="ml-2 text-xs text-slate-400">{r.position}</span> : null}
                    </td>
                    <td className="px-4 py-3 text-xs text-slate-500 dark:text-slate-400">
                      {(r.roles || []).join(' / ') || '—'}
                    </td>
                    <td className="px-4 py-3">
                      <span className={`rounded-full px-2 py-0.5 text-xs ${meta.cls}`}>{meta.label}</span>
                      {r.status === 'DELETED' && r.deletedAt && (
                        <span className="ml-2 text-xs text-slate-400">{formatDateTime(r.deletedAt)}</span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex gap-2 text-xs">
                        {r.status === 'ACTIVE' && (
                          <>
                            <button
                              disabled={busy !== 0 || isSelf}
                              onClick={() => act(r.id, () => userApi.disable(r.id), '已禁用')}
                              className="text-amber-600 hover:underline disabled:opacity-40 dark:text-amber-400"
                            >
                              禁用
                            </button>
                            <button
                              disabled={busy !== 0 || isSelf}
                              onClick={() =>
                                window.confirm(`确定将 ${r.username} 移入回收站？`) &&
                                act(r.id, () => userApi.remove(r.id), '已删除')
                              }
                              className="text-rose-600 hover:underline disabled:opacity-40 dark:text-rose-400"
                            >
                              删除
                            </button>
                          </>
                        )}
                        {r.status === 'INACTIVE' && (
                          <button
                            disabled={busy !== 0}
                            onClick={() => act(r.id, () => userApi.enable(r.id), '已启用')}
                            className="text-emerald-600 hover:underline disabled:opacity-40 dark:text-emerald-400"
                          >
                            启用
                          </button>
                        )}
                        {r.status === 'DELETED' && (
                          <button
                            disabled={busy !== 0}
                            onClick={() => act(r.id, () => userApi.restore(r.id), '已恢复')}
                            className="text-emerald-600 hover:underline disabled:opacity-40 dark:text-emerald-400"
                          >
                            恢复
                          </button>
                        )}
                        {isSelf && <span className="text-slate-400">（当前账号）</span>}
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
