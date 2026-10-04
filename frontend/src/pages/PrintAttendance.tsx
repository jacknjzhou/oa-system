import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import apiClient from '../api/client'
import { checkApi, type CheckMonthDay } from '../api/check'
import { getStoredUser } from '../api/auth'
import { useToast } from '../components/Toast'

interface Company {
  name: string
  shortName: string
}

/** 考勤打印视图：黑白表格 + window.print */
export default function PrintAttendance() {
  const [params] = useSearchParams()
  const { showToast } = useToast()
  const user = getStoredUser()
  const month = params.get('month') ?? new Date().toISOString().slice(0, 7)

  const [rows, setRows] = useState<CheckMonthDay[]>([])
  const [company, setCompany] = useState<Company>({ name: '', shortName: '' })
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    Promise.all([
      checkApi.month(`${month}-01`).catch(() => [] as CheckMonthDay[]),
      apiClient
        .get<Company>('/company')
        .then((r) => r.data)
        .catch(() => ({ name: '', shortName: '' })),
    ]).then(([m, c]) => {
      setRows(m)
      setCompany(c)
      setLoading(false)
    })
  }, [month])

  const fmt = (iso: string | null) => (iso ? iso.replace('T', ' ').slice(11, 16) : '—')
  const fmtMin = (min: number) => (min <= 0 ? '—' : `${Math.floor(min / 60)}h ${min % 60}m`)
  const total = rows.reduce((s, r) => s + r.minutes, 0)

  return (
    <div className="mx-auto max-w-3xl bg-white p-8 text-slate-900">
      <div className="no-print mb-4 flex justify-end gap-2">
        <Link to="/attendance" className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm hover:bg-slate-50">
          返回考勤
        </Link>
        <button
          onClick={() => {
            showToast('已调起打印', 'success')
            window.print()
          }}
          className="rounded-lg bg-slate-800 px-4 py-1.5 text-sm font-medium text-white hover:bg-slate-900"
        >
          打印
        </button>
      </div>

      <div className="print-area border border-slate-300">
        <div className="border-b border-slate-300 p-4 text-center">
          <h1 className="text-xl font-bold">{company.name || 'OA 审批系统'} · 考勤记录</h1>
          <p className="mt-1 text-sm">
            {user?.realName || user?.username || '用户'}（{user?.username}） · {month}
          </p>
        </div>
        {loading ? (
          <p className="p-8 text-center text-sm">加载中…</p>
        ) : (
          <>
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-300 text-center">
                  <th className="py-2">日期</th>
                  <th className="py-2">上班</th>
                  <th className="py-2">下班</th>
                  <th className="py-2">工时</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.date} className="border-b border-slate-100 text-center">
                    <td className="py-1.5">{r.date}</td>
                    <td className="py-1.5">{fmt(r.firstIn)}</td>
                    <td className="py-1.5">{fmt(r.lastOut)}</td>
                    <td className="py-1.5">{fmtMin(r.minutes)}</td>
                  </tr>
                ))}
                {rows.length === 0 && (
                  <tr>
                    <td colSpan={4} className="py-6 text-center text-slate-400">
                      当月无打卡记录
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
            <div className="flex justify-between border-t border-slate-300 p-4 text-sm">
              <span>合计工时：{Math.floor(total / 60)} 小时 {total % 60} 分钟（{rows.length} 个打卡日）</span>
              <span>打印时间：{new Date().toLocaleString('zh-CN')}</span>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
