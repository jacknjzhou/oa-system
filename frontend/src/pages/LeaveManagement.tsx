import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  leaveApi,
  type AdminDepartment,
  type AdminEmployee,
  type LeaveBalance,
  type LeaveUnit,
  type LeaveType,
} from '../api/leave'
import { useToast } from '../components/Toast'
import EmptyState, { ErrorState, LoadingState } from '../components/EmptyState'

const UNIT_LABEL: Record<string, string> = { day: '天', hour: '小时', half_day: '半天' }

function fmtUnit(v: number | null, unit: LeaveUnit | string | null): string {
  if (v === null) return '不限额'
  const n = Math.round(v * 100) / 100
  return `${n}${UNIT_LABEL[unit || 'day'] || ''}`
}

/**
 * 假期管理（HD-01）：部门树（左）+ 员工余额表（右）+ 类型筛选 + 导出。
 * 入口：/leave（leave:manage 权限）；行尾「查看」→ 余额详情页（HD-02）。
 */
export default function LeaveManagement() {
  const navigate = useNavigate()
  const { showToast } = useToast()
  const [types, setTypes] = useState<LeaveType[]>([])
  const [departments, setDepartments] = useState<AdminDepartment[]>([])
  const [orgId, setOrgId] = useState<number | null>(null)
  const [typeCode, setTypeCode] = useState('')
  const [keyword, setKeyword] = useState('')
  const [employees, setEmployees] = useState<AdminEmployee[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [exporting, setExporting] = useState(false)

  useEffect(() => {
    leaveApi
      .listAllTypes()
      .then((ts) => setTypes(ts.filter((t) => t.enabled)))
      .catch(() => setTypes([]))
    leaveApi
      .adminDepartments()
      .then(setDepartments)
      .catch(() => setDepartments([]))
  }, [])

  const load = useCallback(() => {
    setLoading(true)
    setError('')
    leaveApi
      .adminEmployees({ orgId, typeCode: typeCode || null, keyword: keyword || null })
      .then(setEmployees)
      .catch((err) => setError(err instanceof Error ? err.message : '加载失败'))
      .finally(() => setLoading(false))
  }, [orgId, typeCode, keyword])

  useEffect(() => {
    load()
  }, [load])

  const handleExport = async () => {
    setExporting(true)
    try {
      await leaveApi.exportBalancesCsv({ orgId, typeCode: typeCode || null, keyword: keyword || null })
      showToast('导出成功', 'success')
    } catch (err) {
      showToast(err instanceof Error ? err.message : '导出失败', 'error')
    } finally {
      setExporting(false)
    }
  }

  /** 当前筛选下的余额列（类型筛选时单列；全部时前 4 限额类型 + 更多省略） */
  const typeList = typeCode ? types.filter((t) => t.code === typeCode) : types.slice(0, 4)

  if (error && employees.length === 0) {
    return <ErrorState message={error} onRetry={load} />
  }

  return (
    <div className="flex flex-col gap-4 lg:flex-row">
      {/* 左：部门树 */}
      <aside className="w-full shrink-0 rounded-xl border border-slate-200 bg-white p-3 dark:border-slate-700 dark:bg-slate-800 lg:w-48">
        <p className="mb-2 px-2 text-xs font-semibold uppercase text-slate-400">部门</p>
        <ul className="space-y-1">
          <li>
            <button
              onClick={() => setOrgId(null)}
              className={`w-full rounded-lg px-2 py-1.5 text-left text-sm ${
                orgId === null
                  ? 'bg-primary-600 text-white'
                  : 'text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-700'
              }`}
            >
              全部部门
            </button>
          </li>
          {departments.map((d) => (
            <li key={d.id}>
              <button
                onClick={() => setOrgId(d.id)}
                className={`w-full rounded-lg px-2 py-1.5 text-left text-sm ${
                  orgId === d.id
                    ? 'bg-primary-600 text-white'
                    : 'text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-700'
                }`}
              >
                {d.orgName}
                <span className="ml-1 text-xs opacity-60">{d.memberCount}</span>
              </button>
            </li>
          ))}
        </ul>
      </aside>

      {/* 右：员工表 */}
      <div className="min-w-0 flex-1">
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <select
            value={typeCode}
            onChange={(e) => setTypeCode(e.target.value)}
            className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-sm dark:border-slate-600 dark:bg-slate-800 dark:text-slate-200"
          >
            <option value="">全部假期类型</option>
            {types.map((t) => (
              <option key={t.code} value={t.code}>
                {t.name}
              </option>
            ))}
          </select>
          <input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="搜索姓名 / 账号 / 邮箱"
            className="w-56 rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-sm dark:border-slate-600 dark:bg-slate-800 dark:text-slate-200"
          />
          <button
            onClick={handleExport}
            disabled={exporting}
            className="ml-auto rounded-lg bg-rose-500 px-4 py-1.5 text-sm font-medium text-white hover:bg-rose-600 disabled:opacity-50"
          >
            {exporting ? '导出中…' : '导出余额 CSV'}
          </button>
        </div>

        <div className="overflow-hidden rounded-xl border border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-800">
          {loading && employees.length === 0 ? (
            <div className="p-8">
              <LoadingState />
            </div>
          ) : employees.length === 0 ? (
            <div className="p-8">
              <EmptyState title="暂无员工" description="调整部门或筛选条件试试" />
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead className="bg-slate-50 text-left text-xs uppercase text-slate-400 dark:bg-slate-700/50 dark:text-slate-300">
                  <tr>
                    <th className="px-4 py-3">姓名</th>
                    <th className="px-4 py-3">部门</th>
                    <th className="px-4 py-3">职位</th>
                    {typeList.map((t) => (
                      <th key={t.code} className="px-4 py-3">
                        {t.name}
                        <span className="ml-1 normal-case opacity-70">{UNIT_LABEL[t.unit] || ''}</span>
                      </th>
                    ))}
                    <th className="px-4 py-3" />
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-700/60">
                  {employees.map((e) => (
                    <tr key={e.id} className="hover:bg-slate-50 dark:hover:bg-slate-700/40">
                      <td className="px-4 py-2.5 font-medium">{e.realName || e.username}</td>
                      <td className="px-4 py-2.5 text-slate-500 dark:text-slate-400">{e.orgName || '-'}</td>
                      <td className="px-4 py-2.5 text-slate-500 dark:text-slate-400">{e.position || '-'}</td>
                      {typeList.map((t) => {
                        const b: LeaveBalance | undefined = e.balances.find((x) => x.code === t.code)
                        return (
                          <td key={t.code} className="px-4 py-2.5 tabular-nums">
                            {b === undefined ? (
                              <span className="text-slate-300 dark:text-slate-500">—</span>
                            ) : b.limited ? (
                              <span className="text-slate-600 dark:text-slate-300">
                                剩 {fmtUnit(b.available, b.unit)}
                              </span>
                            ) : (
                              <span className="text-slate-400">不限额</span>
                            )}
                          </td>
                        )
                      })}
                      <td className="px-4 py-2.5 text-right">
                        <button
                          onClick={() => navigate(`/leave/balance/${e.id}`)}
                          className="rounded-lg border border-slate-300 px-3 py-1 text-xs text-slate-600 hover:bg-slate-100 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-700"
                        >
                          查看
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
