import { useCallback, useEffect, useState } from 'react'
import apiClient from '../api/client'
import { useToast } from '../components/Toast'
import { formatDateTime } from '../utils/format'

interface CompanyForm {
  name: string
  shortName: string
  logoUrl: string
  address: string
  phone: string
  email: string
  creditCode: string
  description: string
}

interface LoginLogRow {
  id: number
  username: string
  loginTime: string
  ip: string
  userAgent: string
  success: boolean
  failReason: string
}

type Tab = 'company' | 'logs' | 'about'

const EMPTY: CompanyForm = {
  name: '', shortName: '', logoUrl: '', address: '', phone: '', email: '', creditCode: '', description: '',
}

/** 系统设置（SY-04）：企业信息 / 登录日志 / 关于 */
export default function Settings() {
  const { showToast } = useToast()
  const [tab, setTab] = useState<Tab>('company')
  const [company, setCompany] = useState<CompanyForm>(EMPTY)
  const [logs, setLogs] = useState<LoginLogRow[]>([])
  const [logTotal, setLogTotal] = useState(0)
  const [logPage, setLogPage] = useState(0)
  const [logKeyword, setLogKeyword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const loadCompany = useCallback(async () => {
    try {
      const res = await apiClient.get<CompanyForm>('/company')
      setCompany(res.data)
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载失败')
    }
  }, [])

  const loadLogs = useCallback(async () => {
    try {
      setError('')
      const res = await apiClient.get<{ content: LoginLogRow[]; totalElements: number }>(
        '/login-logs',
        { params: { page: logPage, size: 20, username: logKeyword || undefined } }
      )
      setLogs(res.data.content)
      setLogTotal(res.data.totalElements)
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载失败')
    }
  }, [logPage, logKeyword])

  useEffect(() => {
    if (tab === 'company') loadCompany()
    if (tab === 'logs') loadLogs()
  }, [tab, loadCompany, loadLogs])

  const saveCompany = async () => {
    setBusy(true)
    try {
      await apiClient.put('/company', company)
      showToast('已保存', 'success')
    } catch (err) {
      showToast(err instanceof Error ? err.message : '保存失败', 'error')
    } finally {
      setBusy(false)
    }
  }

  const field = (label: string, key: keyof CompanyForm, span = 'md:col-span-1') => (
    <label className={`block ${span}`}>
      <span className="mb-1 block text-xs font-medium text-slate-500 dark:text-slate-400">{label}</span>
      <input
        value={company[key]}
        onChange={(e) => setCompany({ ...company, [key]: e.target.value })}
        className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-700"
      />
    </label>
  )

  return (
    <div className="flex flex-col gap-4">
      <div className="flex gap-1 rounded-xl bg-slate-200/70 p-1 dark:bg-slate-800 w-fit">
        {([
          ['company', '企业信息'],
          ['logs', '登录日志'],
          ['about', '关于系统'],
        ] as [Tab, string][]).map(([t, label]) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className={`rounded-lg px-4 py-1.5 text-sm font-medium transition ${
              tab === t
                ? 'bg-white text-indigo-600 shadow dark:bg-slate-700 dark:text-indigo-300'
                : 'text-slate-500 hover:text-slate-700 dark:text-slate-400'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {tab === 'company' && (
        <div className="rounded-2xl bg-white p-6 shadow-sm dark:bg-slate-800">
          <div className="grid gap-4 md:grid-cols-2">
            {field('企业名称', 'name', 'md:col-span-2')}
            {field('简称', 'shortName')}
            {field('Logo URL', 'logoUrl')}
            {field('地址', 'address', 'md:col-span-2')}
            {field('联系电话', 'phone')}
            {field('邮箱', 'email')}
            {field('统一社会信用代码', 'creditCode', 'md:col-span-2')}
            <label className="block md:col-span-2">
              <span className="mb-1 block text-xs font-medium text-slate-500 dark:text-slate-400">简介</span>
              <textarea
                rows={3}
                value={company.description}
                onChange={(e) => setCompany({ ...company, description: e.target.value })}
                className="w-full rounded-lg border border-slate-200 px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-700"
              />
            </label>
          </div>
          <button
            disabled={busy}
            onClick={saveCompany}
            className="mt-5 rounded-lg bg-indigo-500 px-5 py-2 text-sm font-medium text-white hover:bg-indigo-600 disabled:opacity-50"
          >
            保存
          </button>
        </div>
      )}

      {tab === 'logs' && (
        <div className="rounded-2xl bg-white p-5 shadow-sm dark:bg-slate-800">
          <div className="mb-4 flex items-center gap-2">
            <input
              value={logKeyword}
              onChange={(e) => {
                setLogKeyword(e.target.value)
                setLogPage(0)
              }}
              placeholder="按用户名筛选"
              className="w-56 rounded-lg border border-slate-200 px-3 py-1.5 text-sm dark:border-slate-600 dark:bg-slate-700"
            />
            <button
              onClick={loadLogs}
              className="rounded-lg bg-indigo-500 px-4 py-1.5 text-sm font-medium text-white hover:bg-indigo-600"
            >
              查询
            </button>
            <span className="text-xs text-slate-400">共 {logTotal} 条</span>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-slate-100 text-xs text-slate-400 dark:border-slate-700">
                  <th className="px-3 py-2">用户名</th>
                  <th className="px-3 py-2">时间</th>
                  <th className="px-3 py-2">IP</th>
                  <th className="px-3 py-2">结果</th>
                  <th className="px-3 py-2">原因</th>
                  <th className="px-3 py-2">User-Agent</th>
                </tr>
              </thead>
              <tbody>
                {logs.map((l) => (
                  <tr key={l.id} className="border-b border-slate-50 text-slate-600 dark:border-slate-700/50 dark:text-slate-300">
                    <td className="px-3 py-2 font-medium">{l.username}</td>
                    <td className="px-3 py-2 text-xs">{formatDateTime(l.loginTime)}</td>
                    <td className="px-3 py-2 text-xs">{l.ip}</td>
                    <td className="px-3 py-2">
                      <span className={`rounded-full px-2 py-0.5 text-xs ${
                        l.success
                          ? 'bg-emerald-50 text-emerald-600 dark:bg-emerald-500/15'
                          : 'bg-rose-50 text-rose-600 dark:bg-rose-500/15'
                      }`}>
                        {l.success ? '成功' : '失败'}
                      </span>
                    </td>
                    <td className="px-3 py-2 text-xs">{l.failReason || '—'}</td>
                    <td className="max-w-[240px] truncate px-3 py-2 text-xs text-slate-400">{l.userAgent}</td>
                  </tr>
                ))}
                {logs.length === 0 && (
                  <tr>
                    <td colSpan={6} className="px-3 py-8 text-center text-slate-400">暂无记录</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
          {logTotal > 20 && (
            <div className="mt-3 flex items-center gap-2 text-sm text-slate-500">
              <button
                disabled={logPage === 0}
                onClick={() => setLogPage(logPage - 1)}
                className="rounded-lg border border-slate-200 px-3 py-1 disabled:opacity-40 dark:border-slate-600"
              >
                上一页
              </button>
              第 {logPage + 1} 页
              <button
                disabled={(logPage + 1) * 20 >= logTotal}
                onClick={() => setLogPage(logPage + 1)}
                className="rounded-lg border border-slate-200 px-3 py-1 disabled:opacity-40 dark:border-slate-600"
              >
                下一页
              </button>
            </div>
          )}
        </div>
      )}

      {tab === 'about' && (
        <div className="rounded-2xl bg-white p-6 shadow-sm dark:bg-slate-800 text-sm text-slate-600 dark:text-slate-300 space-y-2">
          <h3 className="text-base font-semibold text-slate-800 dark:text-slate-100">OA 审批系统</h3>
          <p>后端：Spring Boot 3.2 + Flowable 7.0（BPMN 流程引擎）+ MySQL / H2 + Flyway 迁移</p>
          <p>前端：React 18 + Vite + Tailwind + bpmn-js 可视化流程设计器</p>
          <p>能力：动态表单、四态审批（含驳回）、加签/会签、抄送、催办、撤回、审批日志时间线、</p>
          <p>考勤打卡、假期三账本、员工管理（回收站）、职级职称、权组权限、企业信息、登录日志。</p>
        </div>
      )}

      {error && <p className="text-sm text-rose-500">{error}</p>}
    </div>
  )
}
