import { useCallback, useEffect, useState } from 'react'
import { checkApi, type CheckMonthDay, type CheckToday } from '../api/check'
import EmptyState, { ErrorState, LoadingState } from '../components/EmptyState'
import { useToast } from '../components/Toast'
import { formatDateTime } from '../utils/format'

function formatMinutes(min: number): string {
  if (min <= 0) return '—'
  const h = Math.floor(min / 60)
  const m = min % 60
  return h > 0 ? `${h} 小时 ${m} 分钟` : `${m} 分钟`
}

function formatClock(iso: string | null): string {
  if (!iso) return '—'
  const s = formatDateTime(iso)
  return s.includes(' ') ? s.split(' ')[1] : s
}

/** 考勤打卡（AT-01）：今日打卡 + 月度统计。 */
export default function Attendance() {
  const { showToast } = useToast()
  const [today, setToday] = useState<CheckToday | null>(null)
  const [month, setMonth] = useState<CheckMonthDay[]>([])
  const [monthDate, setMonthDate] = useState(() => new Date().toISOString().slice(0, 10))
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [clocking, setClocking] = useState<'in' | 'out' | null>(null)
  const [now, setNow] = useState(() => new Date())

  const load = useCallback(() => {
    setLoading(true)
    Promise.all([checkApi.today(), checkApi.month(monthDate)])
      .then(([t, m]) => {
        setToday(t)
        setMonth(m)
        setError('')
      })
      .catch((err) => setError(err instanceof Error ? err.message : '加载失败'))
      .finally(() => setLoading(false))
  }, [monthDate])

  useEffect(() => {
    load()
  }, [load])

  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), 1000)
    return () => clearInterval(timer)
  }, [])

  const clock = async (type: 'in' | 'out') => {
    if (clocking) return
    setClocking(type)
    try {
      await checkApi.clock(type)
      showToast(type === 'in' ? '上班打卡成功' : '下班打卡成功')
      load()
    } catch (err) {
      showToast(err instanceof Error ? err.message : '打卡失败', 'error')
    } finally {
      setClocking(null)
    }
  }

  const totalMinutes = month.reduce((sum, d) => sum + d.minutes, 0)

  if (loading && !today) return <LoadingState />
  if (error && !today) return <ErrorState message={error} onRetry={load} />

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      {/* 今日打卡 */}
      <div className="rounded-2xl border border-slate-200 bg-white p-6 dark:border-slate-700 dark:bg-slate-800">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div>
            <div className="text-sm text-slate-500 dark:text-slate-400">当前时间</div>
            <div className="mt-1 font-mono text-3xl font-bold tabular-nums">
              {now.toLocaleTimeString('zh-CN', { hour12: false })}
            </div>
          </div>
          <div className="flex items-center gap-3">
            <button
              onClick={() => clock('in')}
              disabled={!!clocking}
              className="rounded-xl bg-emerald-500 px-5 py-3 text-sm font-semibold text-white shadow hover:bg-emerald-600 disabled:opacity-50"
            >
              {clocking === 'in' ? '打卡中…' : '上班打卡'}
            </button>
            <button
              onClick={() => clock('out')}
              disabled={!!clocking}
              className="rounded-xl bg-slate-700 px-5 py-3 text-sm font-semibold text-white shadow hover:bg-slate-800 disabled:opacity-50 dark:bg-slate-600"
            >
              {clocking === 'out' ? '打卡中…' : '下班打卡'}
            </button>
          </div>
        </div>
        {today && (
          <div className="mt-5 grid grid-cols-2 gap-4 border-t border-slate-100 pt-5 text-sm sm:grid-cols-4 dark:border-slate-700">
            <div>
              <div className="text-xs text-slate-500 dark:text-slate-400">上班卡</div>
              <div className="mt-1 font-medium">{formatClock(today.firstIn)}</div>
            </div>
            <div>
              <div className="text-xs text-slate-500 dark:text-slate-400">下班卡</div>
              <div className="mt-1 font-medium">{formatClock(today.lastOut)}</div>
            </div>
            <div>
              <div className="text-xs text-slate-500 dark:text-slate-400">今日工时</div>
              <div className="mt-1 font-medium">{formatMinutes(today.workMinutes)}</div>
            </div>
            <div>
              <div className="text-xs text-slate-500 dark:text-slate-400">今日打卡</div>
              <div className="mt-1 font-medium">{today.records.length} 次</div>
            </div>
          </div>
        )}
      </div>

      {/* 月度统计 */}
      <div className="rounded-2xl border border-slate-200 bg-white p-6 dark:border-slate-700 dark:bg-slate-800">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="font-semibold">月度统计</h2>
          <input
            type="month"
            value={monthDate.slice(0, 7)}
            onChange={(e) => e.target.value && setMonthDate(`${e.target.value}-01`)}
            className="rounded-lg border border-slate-300 bg-transparent px-3 py-1.5 text-sm dark:border-slate-600"
          />
        </div>
        {month.length === 0 ? (
          <div className="mt-4">
            <EmptyState title="本月暂无打卡记录" description="打卡后这里会显示每日工时统计" />
          </div>
        ) : (
          <>
            <div className="mt-4 overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-slate-200 text-left text-xs text-slate-500 dark:border-slate-700 dark:text-slate-400">
                    <th className="pb-2 pr-4 font-medium">日期</th>
                    <th className="pb-2 pr-4 font-medium">上班</th>
                    <th className="pb-2 pr-4 font-medium">下班</th>
                    <th className="pb-2 pr-4 font-medium">工时</th>
                    <th className="pb-2 font-medium">打卡次数</th>
                  </tr>
                </thead>
                <tbody>
                  {[...month].reverse().map((d) => (
                    <tr key={d.date} className="border-b border-slate-100 last:border-0 dark:border-slate-700/60">
                      <td className="py-2.5 pr-4">{d.date}</td>
                      <td className="py-2.5 pr-4 tabular-nums">{formatClock(d.firstIn)}</td>
                      <td className="py-2.5 pr-4 tabular-nums">{formatClock(d.lastOut)}</td>
                      <td className="py-2.5 pr-4">{formatMinutes(d.minutes)}</td>
                      <td className="py-2.5">{d.count} 次</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="mt-3 text-right text-xs text-slate-500 dark:text-slate-400">
              本月累计 {month.length} 个工作日 · 总工时 {formatMinutes(totalMinutes)}
            </div>
          </>
        )}
      </div>
    </div>
  )
}
