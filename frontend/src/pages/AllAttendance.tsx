import { useCallback, useEffect, useState } from 'react'
import { checkApi, type CheckAllRow } from '../api/check'
import { userApi } from '../api/user'
import AttendanceTabs from '../components/AttendanceTabs'
import EmptyState, { ErrorState, LoadingState } from '../components/EmptyState'
import { formatDateTime } from '../utils/format'

/** 全部考勤（只读简化版）：月份 + 人员筛选。状态与 IP 统计将在考勤模块增强中提供。 */
export default function AllAttendance() {
  const [month, setMonth] = useState(() => new Date().toISOString().slice(0, 7))
  const [users, setUsers] = useState<{ id: number; realName: string | null; username: string }[]>([])
  const [userId, setUserId] = useState<number | ''>('')
  const [rows, setRows] = useState<CheckAllRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    userApi
      .list()
      .then((list) => setUsers(list.filter((u) => u.status === 'ACTIVE')))
      .catch(() => setUsers([]))
  }, [])

  const load = useCallback(() => {
    setLoading(true)
    setError('')
    checkApi
      .listAll(`${month}-01`, userId === '' ? null : userId)
      .then((r) => setRows(r))
      .catch((err) => setError(err instanceof Error ? err.message : '加载失败'))
      .finally(() => setLoading(false))
  }, [month, userId])

  useEffect(() => {
    load()
  }, [load])

  if (loading && rows.length === 0) return <LoadingState />
  if (error && rows.length === 0) return <ErrorState message={error} onRetry={load} />

  return (
    <div className="mx-auto max-w-6xl space-y-4">
      <AttendanceTabs />
      <div className="flex flex-wrap items-center gap-3 rounded-xl border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-800">
        <input
          type="month"
          value={month}
          onChange={(e) => setMonth(e.target.value)}
          className="rounded-lg border border-slate-300 bg-transparent px-3 py-1.5 text-sm dark:border-slate-600 dark:text-slate-200"
        />
        <select
          value={userId}
          onChange={(e) => setUserId(e.target.value === '' ? '' : Number(e.target.value))}
          className="rounded-lg border border-slate-300 bg-transparent px-3 py-1.5 text-sm dark:border-slate-600 dark:bg-slate-800 dark:text-slate-200"
        >
          <option value="">全部人员</option>
          {users.map((u) => (
            <option key={u.id} value={u.id}>
              {u.realName || u.username}
            </option>
          ))}
        </select>
        <span className="text-xs text-slate-400">状态与 IP 统计将在考勤模块增强中提供</span>
        <span className="ml-auto text-xs text-slate-400">共 {rows.length} 条打卡记录</span>
      </div>

      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-800">
        {rows.length === 0 ? (
          <div className="p-8">
            <EmptyState title="本月暂无打卡记录" description="员工打卡后会在这里汇总展示" />
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-left text-xs uppercase text-slate-400 dark:bg-slate-700/50 dark:text-slate-300">
              <tr>
                <th className="px-4 py-3">姓名</th>
                <th className="px-4 py-3">账号</th>
                <th className="px-4 py-3">类型</th>
                <th className="px-4 py-3">来源</th>
                <th className="px-4 py-3">备注</th>
                <th className="px-4 py-3">时间</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-700/60">
              {rows.map((r) => (
                <tr key={r.id}>
                  <td className="px-4 py-2.5 font-medium">{r.userName || '-'}</td>
                  <td className="px-4 py-2.5 text-slate-500 dark:text-slate-400">{r.username}</td>
                  <td className="px-4 py-2.5">
                    <span
                      className={
                        r.type === 'in'
                          ? 'rounded-full bg-emerald-100 px-2 py-0.5 text-xs text-emerald-700 dark:bg-emerald-500/20 dark:text-emerald-300'
                          : 'rounded-full bg-rose-100 px-2 py-0.5 text-xs text-rose-700 dark:bg-rose-500/20 dark:text-rose-300'
                      }
                    >
                      {r.type === 'in' ? '上班' : '下班'}
                    </span>
                  </td>
                  <td className="px-4 py-2.5 text-slate-500 dark:text-slate-400">
                    {r.source === 'scan' ? '扫码' : '手动'}
                  </td>
                  <td className="px-4 py-2.5 text-slate-500 dark:text-slate-400">{r.note || '-'}</td>
                  <td className="px-4 py-2.5 tabular-nums text-slate-500 dark:text-slate-400">
                    {formatDateTime(r.checkTime)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
