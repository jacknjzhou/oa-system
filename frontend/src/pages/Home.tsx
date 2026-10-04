import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import apiClient from '../api/client'
import { getMyInstances, getCcInstances } from '../api/process'
import { getTodoTasks, getDoneTasks } from '../api/task'
import { getEnabledApprovalTypes } from '../api/approvalType'
import { getStoredUser } from '../api/auth'
import type { ApprovalType, InstanceDTO, TaskDTO } from '../types'
import { categoryEmoji, formatDateTime } from '../utils/format'
import { useToast } from '../components/Toast'

type Tab = 'mine' | 'todo' | 'done' | 'cc'

interface Company {
  name: string
  shortName: string
}

/** 审批主页（AP-07 模块视图）：申请类型卡片 + 四 Tab 概览 */
export default function Home() {
  const navigate = useNavigate()
  const { showToast } = useToast()
  const user = getStoredUser()

  const [types, setTypes] = useState<ApprovalType[]>([])
  const [mine, setMine] = useState<InstanceDTO[]>([])
  const [todo, setTodo] = useState<TaskDTO[]>([])
  const [done, setDone] = useState<TaskDTO[]>([])
  const [cc, setCc] = useState<InstanceDTO[]>([])
  const [company, setCompany] = useState<Company>({ name: '', shortName: '' })
  const [tab, setTab] = useState<Tab>('todo')
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [t, m, td, dn, c, cp] = await Promise.all([
        getEnabledApprovalTypes().catch(() => [] as ApprovalType[]),
        getMyInstances().catch(() => [] as InstanceDTO[]),
        getTodoTasks().catch(() => [] as TaskDTO[]),
        getDoneTasks().catch(() => [] as TaskDTO[]),
        getCcInstances().catch(() => [] as InstanceDTO[]),
        apiClient
          .get<Company>('/company')
          .then((r) => r.data)
          .catch(() => ({ name: '', shortName: '' })),
      ])
      setTypes(t)
      setMine(m)
      setTodo(td)
      setDone(dn)
      setCc(c)
      setCompany(cp)
    } catch (err) {
      showToast(err instanceof Error ? err.message : '加载失败', 'error')
    } finally {
      setLoading(false)
    }
  }, [showToast])

  useEffect(() => {
    load()
  }, [load])

  const counts: Record<Tab, number> = { mine: mine.length, todo: todo.length, done: done.length, cc: cc.length }

  const tabs = useMemo(
    () =>
      [
        ['todo', `待我审批`, todo.slice(0, 5)],
        ['mine', `我的申请`, mine.slice(0, 5)],
        ['done', `我已审批`, done.slice(0, 5)],
        ['cc', `抄送给我`, cc.slice(0, 5)],
      ] as [Tab, string, (TaskDTO | InstanceDTO)[]][],
    [mine, todo, done, cc]
  )

  return (
    <div className="flex flex-col gap-6">
      {/* 欢迎区 */}
      <div className="rounded-2xl bg-gradient-to-r from-indigo-500 to-violet-500 p-6 text-white shadow-md">
        <p className="text-lg font-semibold">
          {user?.realName || user?.username || '用户'}，欢迎回来
        </p>
        <p className="mt-1 text-sm text-white/80">
          {company.name || 'OA 审批系统'}
          {counts.todo > 0 && ` · 您有 ${counts.todo} 条待办审批`}
        </p>
      </div>

      {/* 申请类型卡片（15 类） */}
      <section>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-sm font-semibold text-slate-700 dark:text-slate-200">创建申请</h2>
          <Link to="/start" className="text-xs text-indigo-500 hover:underline">
            全部申请类型 →
          </Link>
        </div>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
          {types.map((t) => (
            <button
              key={t.id}
              onClick={() => navigate(`/start?type=${encodeURIComponent(t.code)}`)}
              className="flex flex-col items-center gap-2 rounded-2xl bg-white p-4 shadow-sm transition hover:-translate-y-0.5 hover:shadow-md dark:bg-slate-800"
            >
              <span className="text-2xl">{t.icon || categoryEmoji(t.category)}</span>
              <span className="text-sm font-medium text-slate-700 dark:text-slate-200">{t.name}</span>
            </button>
          ))}
          {types.length === 0 && !loading && (
            <p className="col-span-full text-sm text-slate-400">暂无可用审批类型</p>
          )}
        </div>
      </section>

      {/* 四 Tab 概览 */}
      <section>
        <div className="flex gap-1 rounded-xl bg-slate-200/70 p-1 dark:bg-slate-800 w-fit">
          {tabs.map(([t, label]) => (
            <button
              key={t}
              onClick={() => setTab(t)}
              className={`rounded-lg px-4 py-1.5 text-sm font-medium transition ${
                tab === t
                  ? 'bg-white text-indigo-600 shadow dark:bg-slate-700 dark:text-indigo-300'
                  : 'text-slate-500 hover:text-slate-700 dark:text-slate-400'
              }`}
            >
              {label} <span className="text-xs text-slate-400">({counts[t]})</span>
            </button>
          ))}
        </div>

        <div className="mt-3 rounded-2xl bg-white shadow-sm dark:bg-slate-800">
          {tabs
            .filter(([t]) => t === tab)
            .map(([t, label, items]) => (
              <div key={t}>
                <div className="flex items-center justify-between border-b border-slate-100 px-5 py-3 dark:border-slate-700">
                  <h3 className="text-sm font-semibold text-slate-700 dark:text-slate-200">{label}</h3>
                  <Link
                    to={
                      t === 'todo'
                        ? '/tasks/todo'
                        : t === 'done'
                        ? '/tasks/done'
                        : t === 'mine'
                        ? '/my-instances'
                        : '/cc'
                    }
                    className="text-xs text-indigo-500 hover:underline"
                  >
                    查看全部 →
                  </Link>
                </div>
                {items.length === 0 ? (
                  <p className="px-5 py-10 text-center text-sm text-slate-400">暂无数据</p>
                ) : (
                  <ul className="divide-y divide-slate-50 dark:divide-slate-700/50">
                    {items.map((it) => (
                      <li key={it.id}>
                        <Link
                          to={
                            'instanceId' in it
                              ? `/task/${it.id}`
                              : `/tracking/${it.id}`
                          }
                          className="flex items-center gap-4 px-5 py-3 transition hover:bg-slate-50 dark:hover:bg-slate-700/40"
                        >
                          <span className="flex-1 truncate text-sm text-slate-700 dark:text-slate-200">
                            {it.title}
                          </span>
                          {'businessType' in it && (
                            <span className="shrink-0 text-xs text-slate-400">
                              {it.defName}
                            </span>
                          )}
                          <span className="shrink-0 text-xs text-slate-400">
                            {formatDateTime(
                              'createTime' in it
                                ? it.createTime
                                : it.submittedAt
                            ) || '—'}
                          </span>
                          {'status' in it && (
                            <span className={`shrink-0 rounded-full px-2 py-0.5 text-xs ${
                              it.status === 'RUNNING'
                                ? 'bg-amber-50 text-amber-600 dark:bg-amber-500/15'
                                : it.status === 'COMPLETED'
                                ? 'bg-emerald-50 text-emerald-600 dark:bg-emerald-500/15'
                                : it.status === 'DRAFT'
                                ? 'bg-slate-100 text-slate-500 dark:bg-slate-600/40'
                                : 'bg-rose-50 text-rose-600 dark:bg-rose-500/15'
                            }`}>
                              {it.status === 'RUNNING'
                                ? '审批中'
                                : it.status === 'COMPLETED'
                                ? '已通过'
                                : it.status === 'DRAFT'
                                ? '草稿'
                                : it.status === 'CANCELLED'
                                ? '已取消'
                                : '已拒绝'}
                            </span>
                          )}
                        </Link>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            ))}
        </div>
      </section>
    </div>
  )
}
