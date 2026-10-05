import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { leaveApi, type LeaveBalance, type LeaveTransaction } from '../api/leave'
import { userApi, type UserRow } from '../api/user'
import { useToast } from '../components/Toast'
import { formatDateTime } from '../utils/format'
import EmptyState, { ErrorState, LoadingState } from '../components/EmptyState'

const UNIT_LABEL: Record<string, string> = { day: '天', hour: '小时', half_day: '半天' }

function fmtUnit(v: number | null, unit: string | null): string {
  if (v === null) return '不限额'
  const n = Math.round(v * 100) / 100
  return `${n}${UNIT_LABEL[unit || 'day'] || ''}`
}

type ModalKind = 'quota' | 'remaining' | 'log' | null

/** 步骤数值：day/half_day 整数步长，hour 允许 0.5 */
function stepFor(unit: string | null): number {
  return unit === 'hour' ? 0.5 : 1
}

/**
 * 假期余额详情（HD-02/03/07）：/leave/balance/:userId
 * 三按钮——编辑剩余时长（SET_REMAINING）/ 假期时长（SET_QUOTA）/ 日志。
 */
export default function LeaveBalanceDetail() {
  const { userId } = useParams()
  const uid = Number(userId)
  const { showToast } = useToast()

  const [balances, setBalances] = useState<LeaveBalance[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [modal, setModal] = useState<ModalKind>(null)
  const [modalCode, setModalCode] = useState('')
  const [value, setValue] = useState('')
  const [remark, setRemark] = useState('')
  const [saving, setSaving] = useState(false)
  const [logs, setLogs] = useState<LeaveTransaction[]>([])
  const [logsTotal, setLogsTotal] = useState(0)
  const [user, setUser] = useState<UserRow | null>(null)

  const load = useCallback(() => {
    if (!Number.isFinite(uid) || uid <= 0) return
    setLoading(true)
    setError('')
    leaveApi
      .adminBalances(uid)
      .then(setBalances)
      .catch((err) => setError(err instanceof Error ? err.message : '加载失败'))
      .finally(() => setLoading(false))
    userApi
      .list()
      .then((list) => setUser(list.find((u) => u.id === uid) ?? null))
      .catch(() => setUser(null))
  }, [uid])

  useEffect(() => {
    load()
  }, [load])

  const openEdit = (kind: Exclude<ModalKind, null | 'log'>, b: LeaveBalance) => {
    setModal(kind)
    setModalCode(b.code)
    setValue(b.available === null ? '' : String(b.available))
    setRemark('')
  }

  const openLog = (code: string) => {
    setModal('log')
    setModalCode(code)
    leaveApi
      .adminLogs({ userId: uid, typeCode: code, page: 0, size: 50 })
      .then((r) => {
        setLogs(r.items)
        setLogsTotal(r.total)
      })
      .catch((err) => showToast(err instanceof Error ? err.message : '加载失败', 'error'))
  }

  const submitEdit = async () => {
    const num = Number(value)
    if (!Number.isFinite(num) || num < 0) {
      showToast('请输入不小于 0 的数值', 'error')
      return
    }
    setSaving(true)
    try {
      await leaveApi.adminGrant({
        userId: uid,
        typeCode: modalCode,
        action: modal === 'quota' ? 'SET_QUOTA' : 'SET_REMAINING',
        amount: num,
        remark: remark || null,
      })
      showToast(modal === 'quota' ? '额度已更新' : '剩余时长已修正', 'success')
      setModal(null)
      load()
    } catch (err) {
      showToast(err instanceof Error ? err.message : '操作失败', 'error')
    } finally {
      setSaving(false)
    }
  }

  if (!Number.isFinite(uid) || uid <= 0) {
    return <ErrorState message="参数错误" onRetry={() => history.back()} />
  }
  if (loading && balances.length === 0) return <LoadingState />
  if (error && balances.length === 0) return <ErrorState message={error} onRetry={load} />

  const target = balances.find((b) => b.code === modalCode)

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <div>
        <h2 className="text-lg font-semibold">
          假期余额 — {user ? `${user.realName || user.username}` : `员工 ${uid}`}
          {user?.orgName ? <span className="ml-2 text-sm font-normal text-slate-400">{user.orgName}</span> : null}
        </h2>
        <p className="mt-1 text-sm text-slate-400">
          编辑剩余时长 / 假期时长后即时生效并记入假期日志；不限额类型无剩余时长概念
        </p>
      </div>

      <div className="overflow-hidden rounded-xl border border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-800">
        {balances.length === 0 ? (
          <div className="p-8">
            <EmptyState title="该员工暂无假期余额" description="授予额度后会在这里展示" />
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-left text-xs uppercase text-slate-400 dark:bg-slate-700/50 dark:text-slate-300">
              <tr>
                <th className="px-4 py-3">名称</th>
                <th className="px-4 py-3">假期时长（额度）</th>
                <th className="px-4 py-3">已用</th>
                <th className="px-4 py-3">剩余</th>
                <th className="px-4 py-3">时长单位</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-700/60">
              {balances.map((b) => (
                <tr key={b.code} className="hover:bg-slate-50 dark:hover:bg-slate-700/40">
                  <td className="px-4 py-3 font-medium">{b.name}</td>
                  <td className="px-4 py-3 tabular-nums text-slate-500 dark:text-slate-400">
                    {fmtUnit(b.limited ? b.quota : null, b.unit)}
                  </td>
                  <td className="px-4 py-3 tabular-nums text-slate-500 dark:text-slate-400">
                    {fmtUnit(b.limited ? b.used : null, b.unit)}
                  </td>
                  <td className="px-4 py-3 tabular-nums">
                    <span
                      className={
                        b.limited && b.available !== null && b.available < 0
                          ? 'font-semibold text-rose-600 dark:text-rose-400'
                          : 'font-medium'
                      }
                    >
                      {fmtUnit(b.limited ? b.available : null, b.unit)}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-slate-500 dark:text-slate-400">
                    {UNIT_LABEL[b.unit] || b.unit}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex flex-wrap gap-2">
                      <button
                        onClick={() => openEdit('remaining', b)}
                        disabled={!b.limited}
                        title={!b.limited ? '不限额类型无剩余时长概念' : '设定剩余时长（反推额度）'}
                        className="rounded-lg bg-blue-500 px-3 py-1 text-xs font-medium text-white hover:bg-blue-600 disabled:cursor-not-allowed disabled:opacity-40"
                      >
                        编辑剩余时长
                      </button>
                      <button
                        onClick={() => openEdit('quota', b)}
                        disabled={!b.limited}
                        title={!b.limited ? '不限额类型无额度概念' : '直接设定额度总量'}
                        className="rounded-lg bg-rose-500 px-3 py-1 text-xs font-medium text-white hover:bg-rose-600 disabled:cursor-not-allowed disabled:opacity-40"
                      >
                        假期时长
                      </button>
                      <button
                        onClick={() => openLog(b.code)}
                        className="rounded-lg border border-rose-300 px-3 py-1 text-xs font-medium text-rose-600 hover:bg-rose-50 dark:border-rose-500/50 dark:text-rose-400 dark:hover:bg-rose-500/10"
                      >
                        日志
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* 编辑弹窗 */}
      {modal && modal !== 'log' && target && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl dark:bg-slate-800">
            <h3 className="font-semibold">
              {modal === 'quota' ? `设定 ${target.name} 假期时长` : `编辑 ${target.name} 剩余时长`}
            </h3>
            <div className="mt-4 space-y-3">
              <div className="flex items-center gap-2">
                <input
                  type="number"
                  min={0}
                  step={stepFor(target.unit)}
                  value={value}
                  onChange={(e) => setValue(e.target.value)}
                  autoFocus
                  className="w-40 rounded-lg border border-slate-300 bg-transparent px-3 py-2 text-sm dark:border-slate-600 dark:text-slate-200"
                />
                <span className="text-sm text-slate-400">{UNIT_LABEL[target.unit] || ''}</span>
              </div>
              <p className="text-xs text-slate-400">
                {modal === 'quota'
                  ? '额度总量将被设定为该值（不能低于已用+冻结）'
                  : '剩余时长将被修正，额度按 已用+冻结+剩余 反推'}
              </p>
              <input
                value={remark}
                onChange={(e) => setRemark(e.target.value)}
                placeholder="备注（记入假期日志，可选）"
                className="w-full rounded-lg border border-slate-300 bg-transparent px-3 py-2 text-sm dark:border-slate-600 dark:text-slate-200"
              />
            </div>
            <div className="mt-5 flex justify-end gap-2">
              <button
                onClick={() => setModal(null)}
                className="rounded-lg border border-slate-300 px-4 py-2 text-sm text-slate-600 hover:bg-slate-50 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-700"
              >
                取消
              </button>
              <button
                onClick={submitEdit}
                disabled={saving}
                className="rounded-lg bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 disabled:opacity-50"
              >
                {saving ? '保存中…' : '保存'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 日志弹窗 */}
      {modal === 'log' && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="max-h-[80vh] w-full max-w-3xl overflow-hidden rounded-2xl bg-white shadow-xl dark:bg-slate-800">
            <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4 dark:border-slate-700">
              <h3 className="font-semibold">
                假期日志 — {target?.name ?? modalCode}
                <span className="ml-2 text-xs font-normal text-slate-400">共 {logsTotal} 条</span>
              </h3>
              <button
                onClick={() => setModal(null)}
                className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-200"
              >
                ✕
              </button>
            </div>
            <div className="max-h-[65vh] overflow-y-auto">
              {logs.length === 0 ? (
                <div className="p-8">
                  <EmptyState title="暂无日志" />
                </div>
              ) : (
                <table className="w-full text-sm">
                  <thead className="sticky top-0 bg-slate-50 text-left text-xs uppercase text-slate-400 dark:bg-slate-700/90 dark:text-slate-300">
                    <tr>
                      <th className="px-4 py-2.5">类型</th>
                      <th className="px-4 py-2.5">变动</th>
                      <th className="px-4 py-2.5">操作人</th>
                      <th className="px-4 py-2.5">对象</th>
                      <th className="px-4 py-2.5">说明</th>
                      <th className="px-4 py-2.5">备注</th>
                      <th className="px-4 py-2.5">时间</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 dark:divide-slate-700/60">
                    {logs.map((t, i) => (
                      <tr key={t.id ?? i}>
                        <td className="px-4 py-2.5">
                          <span className="rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-500 dark:bg-slate-700 dark:text-slate-300">
                            {t.typeLabel}
                          </span>
                        </td>
                        <td className="px-4 py-2.5 tabular-nums">
                          <span className={t.delta > 0 ? 'text-emerald-600 dark:text-emerald-400' : 'text-rose-600 dark:text-rose-400'}>
                            {t.delta > 0 ? `+${t.delta}` : t.delta}
                          </span>
                          <span className="ml-0.5 text-xs text-slate-400">{UNIT_LABEL[t.unit] || ''}</span>
                        </td>
                        <td className="px-4 py-2.5 text-slate-500 dark:text-slate-400">{t.operatorName || '-'}</td>
                        <td className="px-4 py-2.5 text-slate-500 dark:text-slate-400">{t.acceptName || '-'}</td>
                        <td className="px-4 py-2.5 text-slate-500 dark:text-slate-400">{t.reason || '-'}</td>
                        <td className="px-4 py-2.5 text-slate-500 dark:text-slate-400">
                          {t.remark || (t.instanceId != null ? (
                            <Link
                              to={`/tracking/${t.instanceId}`}
                              className="text-primary-600 hover:underline dark:text-primary-400"
                            >
                              查看关联流程
                            </Link>
                          ) : '-')}
                        </td>
                        <td className="px-4 py-2.5 text-xs tabular-nums text-slate-400">
                          {formatDateTime(t.createdAt)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
