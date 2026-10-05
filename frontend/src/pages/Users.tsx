import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { userApi, type JobLevelRow, type UserRow } from '../api/user'
import { orgApi, type OrgNode } from '../api/org'
import EmptyState, { ErrorState, LoadingState } from '../components/EmptyState'
import { useToast } from '../components/Toast'
import { getStoredUser } from '../api/auth'
import { formatDateTime } from '../utils/format'
import EmployeeFormModal from '../components/EmployeeFormModal'

type SubTab = 'active' | 'disabled' | 'deleted' | 'all'

const SUB_TABS: { key: SubTab; label: string }[] = [
  { key: 'active', label: '在职' },
  { key: 'disabled', label: '已禁用' },
  { key: 'deleted', label: '回收站' },
  { key: 'all', label: '全部' },
]

const STATUS_META: Record<UserRow['status'], { label: string; cls: string }> = {
  ACTIVE: { label: '正常', cls: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/40 dark:text-emerald-300' },
  INACTIVE: { label: '已禁用', cls: 'bg-amber-100 text-amber-700 dark:bg-amber-900/40 dark:text-amber-300' },
  LOCKED: { label: '已锁定', cls: 'bg-rose-100 text-rose-700 dark:bg-rose-900/40 dark:text-rose-300' },
  DELETED: { label: '已删除', cls: 'bg-slate-200 text-slate-500 dark:bg-slate-700 dark:text-slate-400' },
}

function flatten(list: OrgNode[]): OrgNode[] {
  const out: OrgNode[] = []
  const walk = (n: OrgNode) => {
    out.push(n)
    n.children.forEach(walk)
  }
  list.forEach(walk)
  return out
}

/**
 * 员工管理（SY-01 / 6.3.1 / 6.3.2）：
 * 子 Tab（在职/已禁用/回收站/全部，6.4.5-7 停用与已删除互斥归类）+ 部门/关键字筛选（6.3.2）
 * + 九列表格（性别/部门/职称/职级/主管/最近登录）+ 创建/编辑弹窗 + 禁用/恢复/删除。
 * 停用/删除自守卫（6.4.5-6）；已删除进回收站可恢复。
 */
export default function Users() {
  const { showToast } = useToast()
  const me = getStoredUser()
  const [searchParams, setSearchParams] = useSearchParams()
  const canManage =
    (me?.roles ?? []).includes('ADMIN') || (me?.permissions ?? []).includes('hr:user')

  const [tab, setTab] = useState<SubTab>('active')
  const [keyword, setKeyword] = useState('')
  const [orgId, setOrgId] = useState<number | ''>('')
  const [orgs, setOrgs] = useState<OrgNode[]>([])
  const [rows, setRows] = useState<UserRow[]>([])
  const [levels, setLevels] = useState<JobLevelRow[]>([])
  const [supervisors, setSupervisors] = useState<UserRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(0)
  const [modal, setModal] = useState<{ mode: 'create' | 'edit'; row?: UserRow } | null>(null)
  const [pendingCreateOrg, setPendingCreateOrg] = useState<number | null | undefined>(undefined)

  // ?create=1&org=123（部门页入口，验收 3）
  useEffect(() => {
    if (searchParams.get('create') === '1') {
      const org = searchParams.get('org')
      setModal({ mode: 'create' })
      setPendingCreateOrg(org ? Number(org) : null)
      searchParams.delete('create')
      searchParams.delete('org')
      setSearchParams(searchParams, { replace: true })
    }
  }, [searchParams, setSearchParams])

  useEffect(() => {
    orgApi
      .tree()
      .then((t) => setOrgs(flatten(t)))
      .catch(() => setOrgs([]))
    userApi.jobLevels(true).then(setLevels).catch(() => setLevels([]))
    userApi
      .list({ status: 'active' })
      .then(setSupervisors)
      .catch(() => setSupervisors([]))
  }, [])

  const load = useCallback(() => {
    setLoading(true)
    setError('')
    userApi
      .list({ status: tab, orgId: orgId === '' ? null : orgId, keyword: keyword.trim() || null })
      .then(setRows)
      .catch((err) => setError(err instanceof Error ? err.message : '加载失败'))
      .finally(() => setLoading(false))
  }, [tab, keyword, orgId])

  useEffect(() => {
    void load()
  }, [load])

  const levelName = (id?: number | null) =>
    id == null ? '—' : levels.find((l) => l.id === id)?.name ?? `#${id}`

  const supervisorName = (id?: number | null) => {
    if (id == null) return '—'
    const s = supervisors.find((x) => x.id === id)
    return s ? (s.realName ?? s.username) : `#${id}`
  }

  const disable = async (r: UserRow) => {
    if (me && Number(me.id) === r.id) {
      showToast('不能禁用当前登录用户', 'error')
      return
    }
    if (!window.confirm(`确定禁用「${r.realName ?? r.username}」？（其部门人数与候选人将同步减少）`)) return
    setBusy(1)
    try {
      await userApi.disable(r.id)
      showToast('已禁用')
      setBusy(0)
      void load()
    } catch (e) {
      showToast(e instanceof Error ? e.message : '操作失败', 'error')
      setBusy(0)
    }
  }

  const remove = async (r: UserRow) => {
    if (me && Number(me.id) === r.id) {
      showToast('不能删除当前登录用户（请使用禁用）', 'error')
      return
    }
    if (!window.confirm(`确定删除「${r.realName ?? r.username}」？（进入回收站，可恢复）`)) return
    setBusy(1)
    try {
      await userApi.remove(r.id)
      showToast('已删除（回收站）')
      setBusy(0)
      void load()
    } catch (e) {
      showToast(e instanceof Error ? e.message : '操作失败', 'error')
      setBusy(0)
    }
  }

  const restore = async (r: UserRow) => {
    setBusy(1)
    try {
      if (r.status === 'DELETED') await userApi.restore(r.id)
      else await userApi.enable(r.id)
      showToast('已恢复')
      setBusy(0)
      void load()
    } catch (e) {
      showToast(e instanceof Error ? e.message : '操作失败', 'error')
      setBusy(0)
    }
  }

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <div className="mr-auto flex rounded-lg border border-slate-200 bg-white p-1 dark:border-slate-700 dark:bg-slate-800">
          {SUB_TABS.map((t) => (
            <button
              key={t.key}
              onClick={() => setTab(t.key)}
              className={`rounded-md px-3 py-1.5 text-sm ${
                tab === t.key
                  ? 'bg-primary-600 text-white'
                  : 'text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-700'
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>
        <select
          value={orgId}
          onChange={(e) => setOrgId(e.target.value ? Number(e.target.value) : '')}
          className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-800"
        >
          <option value="">全部部门</option>
          {orgs.map((o) => (
            <option key={o.id} value={o.id}>
              {o.orgName}
            </option>
          ))}
        </select>
        <input
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          placeholder="搜索姓名/账号/手机/工号"
          className="w-52 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-800"
        />
        {canManage && tab !== 'deleted' && (
          <button
            onClick={() => setModal({ mode: 'create' })}
            className="rounded-lg bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700"
          >
            创建新员工
          </button>
        )}
      </div>

      <div className="rounded-xl border border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-800">
        {loading ? (
          <LoadingState />
        ) : error ? (
          <ErrorState onRetry={load} message={error} />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-100 text-left text-xs text-slate-400 dark:border-slate-700">
                  <th className="px-5 py-3">姓名</th>
                  <th className="px-3 py-3">账号</th>
                  <th className="px-3 py-3">部门</th>
                  <th className="px-3 py-3">职称</th>
                  <th className="px-3 py-3">职级</th>
                  <th className="px-3 py-3">主管</th>
                  <th className="px-3 py-3">状态</th>
                  <th className="px-3 py-3">最近登录</th>
                  <th className="px-5 py-3 text-right">操作</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.id} className="border-b border-slate-50 hover:bg-slate-50/50 dark:border-slate-700/50">
                    <td className="px-5 py-3 font-medium text-slate-700 dark:text-slate-200">
                      {r.realName ?? r.username}
                      {r.gender === 'male' && <span className="ml-1 text-xs text-slate-400">男</span>}
                      {r.gender === 'female' && <span className="ml-1 text-xs text-slate-400">女</span>}
                    </td>
                    <td className="px-3 py-3 text-slate-500">{r.username}</td>
                    <td className="px-3 py-3 text-slate-500">{r.orgName ?? '—'}</td>
                    <td className="px-3 py-3 text-slate-500">{r.jobTitleName ?? '—'}</td>
                    <td className="px-3 py-3 text-slate-500">{levelName(r.jobLevelId)}</td>
                    <td className="px-3 py-3 text-slate-500">{supervisorName(r.supervisorId)}</td>
                    <td className="px-3 py-3">
                      <span
                        className={`rounded-full px-2 py-0.5 text-xs ${STATUS_META[r.status].cls}`}
                        title={r.status === 'DELETED' && r.deletedAt ? formatDateTime(r.deletedAt) : undefined}
                      >
                        {STATUS_META[r.status].label}
                      </span>
                    </td>
                    <td className="px-3 py-3 text-xs text-slate-400">
                      {r.lastLoginAt ? formatDateTime(r.lastLoginAt) : '—'}
                    </td>
                    <td className="px-5 py-3 text-right">
                      <div className="flex justify-end gap-2 text-xs">
                        {canManage && tab !== 'deleted' && (
                          <button
                            onClick={() => setModal({ mode: 'edit', row: r })}
                            className="text-primary-600 hover:underline"
                          >
                            编辑
                          </button>
                        )}
                        {r.status === 'ACTIVE' && canManage && (
                          <button
                            disabled={busy === r.id}
                            onClick={() => void disable(r)}
                            className="text-amber-600 hover:underline disabled:opacity-50"
                          >
                            禁用
                          </button>
                        )}
                        {r.status !== 'ACTIVE' && canManage && (
                          <button
                            disabled={busy === r.id}
                            onClick={() => void restore(r)}
                            className="text-emerald-600 hover:underline disabled:opacity-50"
                          >
                            {r.status === 'DELETED' ? '恢复' : '启用'}
                          </button>
                        )}
                        {tab !== 'deleted' && canManage && (
                          <button
                            disabled={busy === r.id}
                            onClick={() => void remove(r)}
                            className="text-red-500 hover:underline disabled:opacity-50"
                          >
                            删除
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {rows.length === 0 && <EmptyState title="无匹配员工" />}
          </div>
        )}
      </div>

      {modal && (
        <EmployeeFormModal
          mode={modal.mode}
          initial={modal.row}
          defaultOrgId={modal.mode === 'create' ? (pendingCreateOrg ?? null) : null}
          canManage={canManage}
          onClose={() => {
            setModal(null)
            setPendingCreateOrg(undefined)
          }}
          onSaved={() => {
            setModal(null)
            setPendingCreateOrg(undefined)
            void load()
          }}
        />
      )}
    </div>
  )
}
