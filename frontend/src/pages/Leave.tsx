import { useCallback, useEffect, useState } from 'react'
import { leaveApi, type LeaveLedgerRow, type LeaveType } from '../api/leave'
import EmptyState, { ErrorState, LoadingState } from '../components/EmptyState'
import { formatDateTime } from '../utils/format'

const QUOTA_LABEL: Record<string, string> = {
  fixed: '定量',
  accrual: '累积',
  none: '不限',
}

/** 我的假期（AT-03）：三账本视图——额度 / 已用 / 冻结 / 余额 + 流水。 */
export default function Leave() {
  const [types, setTypes] = useState<LeaveType[]>([])
  const [ledgers, setLedgers] = useState<Record<string, LeaveLedgerRow>>({})
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(() => {
    setLoading(true)
    setError('')
    leaveApi
      .types()
      .then(async (ts) => {
        setTypes(ts)
        const entries = await Promise.all(
          ts.map(async (t) => {
            const rows = await leaveApi.ledger(t.code)
            return [t.code, rows[0]] as const
          })
        )
        setLedgers(Object.fromEntries(entries.filter(([, r]) => r !== undefined)))
      })
      .catch((err) => setError(err instanceof Error ? err.message : '加载失败'))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    load()
  }, [load])

  if (loading && types.length === 0) return <LoadingState />
  if (error && types.length === 0) return <ErrorState message={error} onRetry={load} />

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {types.map((t) => {
          const row = ledgers[t.code]
          const unlimited = t.quotaType === 'none'
          const pct = unlimited || !row || row.quota === 0 ? 0 : Math.min(100, (row.used / row.quota) * 100)
          return (
            <div
              key={t.code}
              className="rounded-2xl border border-slate-200 bg-white p-5 dark:border-slate-700 dark:bg-slate-800"
            >
              <div className="flex items-center justify-between">
                <div className="font-medium">{t.name}</div>
                <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-500 dark:bg-slate-700 dark:text-slate-300">
                  {QUOTA_LABEL[t.quotaType] || t.quotaType}
                </span>
              </div>
              {unlimited ? (
                <div className="mt-3 text-2xl font-bold text-slate-400">不限额度</div>
              ) : (
                <>
                  <div className="mt-3 text-2xl font-bold">
                    {row ? row.available : 0}
                    <span className="ml-1 text-sm font-normal text-slate-400">天可用</span>
                  </div>
                  <div className="mt-2 h-2 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-700">
                    <div
                      className="h-full rounded-full bg-primary-500 transition-all"
                      style={{ width: `${pct}%` }}
                    />
                  </div>
                  <div className="mt-2 flex justify-between text-xs text-slate-500 dark:text-slate-400">
                    <span>额度 {row ? row.quota : 0}</span>
                    <span>已用 {row ? row.used : 0}</span>
                    <span>冻结 {row ? row.frozen : 0}</span>
                  </div>
                </>
              )}
            </div>
          )
        })}
      </div>

      {/* 流水 */}
      <div className="rounded-2xl border border-slate-200 bg-white p-6 dark:border-slate-700 dark:bg-slate-800">
        <h2 className="font-semibold">假期流水</h2>
        {Object.values(ledgers).every((r) => r.transactions.length === 0) ? (
          <div className="mt-4">
            <EmptyState title="暂无流水" description="请假审批通过/冲销后会在这里留痕" />
          </div>
        ) : (
          <div className="mt-4 space-y-3">
            {Object.entries(ledgers)
              .filter(([, r]) => r.transactions.length > 0)
              .flatMap(([code, r]) =>
                r.transactions.map((tx) => (
                  <div
                    key={`${code}-${tx.createdAt}-${tx.delta}`}
                    className="flex items-center justify-between rounded-xl border border-slate-100 px-4 py-3 text-sm dark:border-slate-700/60"
                  >
                    <div className="flex items-center gap-3">
                      <span
                        className={
                          tx.delta > 0
                            ? 'font-semibold text-emerald-600 dark:text-emerald-400'
                            : 'font-semibold text-rose-600 dark:text-rose-400'
                        }
                      >
                        {tx.delta > 0 ? `+${tx.delta}` : tx.delta} 天
                      </span>
                      <span className="font-medium">{r.name}</span>
                      <span className="text-xs text-slate-400">{tx.reason}</span>
                      {tx.refInstanceNo && (
                        <span className="text-xs text-slate-400">单号 {tx.refInstanceNo}</span>
                      )}
                    </div>
                    <span className="text-xs tabular-nums text-slate-400">{formatDateTime(tx.createdAt)}</span>
                  </div>
                ))
              )}
          </div>
        )}
      </div>
    </div>
  )
}
