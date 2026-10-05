import { useCallback, useEffect, useState } from 'react'
import { userApi, type DictRow } from '../api/user'
import { useToast } from '../components/Toast'
import EmptyState, { ErrorState, LoadingState } from '../components/EmptyState'

/** 职称字典（SY-04）：新建/启停（T5 追加：改名/删除/被引用提示）。 */
export default function JobTitles() {
  const { showToast } = useToast()
  const [rows, setRows] = useState<DictRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [name, setName] = useState('')
  const [code, setCode] = useState('')

  const refresh = useCallback(async () => {
    try {
      setError('')
      setRows(await userApi.jobTitles(true))
    } catch {
      setError('加载失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const create = async () => {
    if (!name.trim()) {
      showToast('名称必填', 'error')
      return
    }
    try {
      await userApi.createJobTitle({ name: name.trim(), code: code.trim() || null })
      showToast('已创建')
      setName('')
      setCode('')
      await refresh()
    } catch (e) {
      showToast(e instanceof Error ? e.message : '创建失败', 'error')
    }
  }

  const toggle = async (r: DictRow) => {
    try {
      await userApi.updateJobTitle(r.id, { name: r.name, enabled: !r.enabled })
      await refresh()
    } catch (e) {
      showToast(e instanceof Error ? e.message : '操作失败', 'error')
    }
  }

  return (
    <div className="rounded-xl border border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-800">
      <div className="flex flex-wrap items-center gap-2 border-b border-slate-100 px-5 py-4 dark:border-slate-700">
        <div className="mr-auto">
          <h3 className="text-sm font-semibold text-slate-800 dark:text-slate-100">职称字典</h3>
          <p className="text-xs text-slate-400">员工档案「职称」可选值；code 不可改（引用保护）</p>
        </div>
        <input
          value={code}
          onChange={(e) => setCode(e.target.value)}
          placeholder="编码（缺省=名称）"
          className="w-36 rounded-lg border border-slate-300 px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-700"
        />
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="名称"
          className="w-36 rounded-lg border border-slate-300 px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-700"
        />
        <button
          onClick={() => void create()}
          className="rounded-lg bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700"
        >
          新建
        </button>
      </div>
      {loading ? (
        <LoadingState />
      ) : error ? (
        <ErrorState onRetry={refresh} message={error} />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-100 text-left text-xs text-slate-400 dark:border-slate-700">
                <th className="px-5 py-3">名称</th>
                <th className="px-3 py-3">编码</th>
                <th className="px-3 py-3">状态</th>
                <th className="px-5 py-3 text-right">操作</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id} className="border-b border-slate-50 hover:bg-slate-50/50 dark:border-slate-700/50">
                  <td className="px-5 py-3 text-slate-700 dark:text-slate-200">{r.name}</td>
                  <td className="px-3 py-3 text-slate-500">{r.code}</td>
                  <td className="px-3 py-3">
                    <span
                      className={`rounded-full px-2 py-0.5 text-xs ${
                        r.enabled
                          ? 'bg-emerald-50 text-emerald-700'
                          : 'bg-slate-100 text-slate-500 dark:bg-slate-700'
                      }`}
                    >
                      {r.enabled ? '启用' : '已屏蔽'}
                    </span>
                  </td>
                  <td className="px-5 py-3 text-right">
                    <button
                      onClick={() => void toggle(r)}
                      className="text-xs text-primary-600 hover:underline"
                    >
                      {r.enabled ? '屏蔽' : '启用'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {rows.length === 0 && <EmptyState title="暂无职称" />}
        </div>
      )}
    </div>
  )
}
