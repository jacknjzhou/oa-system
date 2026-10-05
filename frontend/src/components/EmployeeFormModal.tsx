import { useEffect, useState } from 'react'
import { getRoles, userApi, type UserCreateBody, type UserRow, type UserUpdateBody, type JobLevelRow } from '../api/user'
import { orgApi, type OrgNode } from '../api/org'
import { useToast } from './Toast'

export interface EmployeeFormState {
  username: string
  password: string
  realName: string
  gender: string
  position: string
  phone: string
  email: string
  orgId: number | ''
  supervisorId: number | ''
  jobLevelId: number | ''
  jobTitleId: number | ''
  roleCodes: string[]
}

interface Props {
  mode: 'create' | 'edit'
  initial?: UserRow
  /** create 模式默认部门（部门页「创建新员工」传入，验收 3） */
  defaultOrgId?: number | null
  canManage: boolean
  onClose: () => void
  onSaved: () => void
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

const inputCls =
  'w-full rounded-lg border border-slate-300 px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-700'

/**
 * 员工表单弹窗（6.3.1/6.3.2/6.4.4/6.4.5）：
 * 创建：用户名(正则)+初始密码(≥8)+姓名+性别+部门(必选可空)+岗位/手机/邮箱+主管(仅在职)+职级/职称(仅启用)+角色
 * 编辑：除 username 只读外同构；调岗=orgId 变更；「解除部门/解除职称」= clearOrg/clearJobTitle。
 */
export default function EmployeeFormModal({ mode, initial, defaultOrgId, canManage, onClose, onSaved }: Props) {
  const { showToast } = useToast()
  const [form, setForm] = useState<EmployeeFormState>({
    username: initial?.username ?? '',
    password: '',
    realName: initial?.realName ?? '',
    gender: initial?.gender ?? '',
    position: initial?.position ?? '',
    phone: initial?.phone ?? '',
    email: initial?.email ?? '',
    orgId: initial?.orgId ?? (mode === 'create' ? (defaultOrgId ?? '') : ''),
    supervisorId: initial?.supervisorId ?? '',
    jobLevelId: initial?.jobLevelId ?? '',
    jobTitleId: initial?.jobTitleId ?? '',
    roleCodes: initial?.roles ?? [],
  })
  const [orgs, setOrgs] = useState<OrgNode[]>([])
  const [levels, setLevels] = useState<JobLevelRow[]>([])
  const [titles, setTitles] = useState<{ id: number; name: string }[]>([])
  const [candidates, setCandidates] = useState<UserRow[]>([])
  const [roles, setRoles] = useState<string[]>([])
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    orgApi.tree().then((t) => setOrgs(flatten(t))).catch(() => setOrgs([]))
    userApi
      .jobLevels(true)
      .then((ls) => setLevels(ls.filter((l) => l.enabled)))
      .catch(() => setLevels([]))
    userApi
      .jobTitles(true)
      .then((ts) => setTitles(ts.filter((t) => t.enabled).map((t) => ({ id: t.id, name: t.name }))))
      .catch(() => setTitles([]))
    // 主管候选：在职员工（验收 4），排除本人（编辑时）
    userApi
      .list({ status: 'active' })
      .then((us) => setCandidates(us.filter((u) => (initial ? u.id !== initial.id : true))))
      .catch(() => setCandidates([]))
    getRoles()
      .then((rs) => setRoles(rs.map((r) => r.code)))
      .catch(() => setRoles([]))
  }, [initial])

  const set = <K extends keyof EmployeeFormState>(k: K, v: EmployeeFormState[K]) =>
    setForm((f) => ({ ...f, [k]: v }))

  const submit = async () => {
    if (!form.realName.trim()) {
      showToast('姓名必填', 'error')
      return
    }
    if (mode === 'create') {
      if (!/^[a-z0-9_.-]{2,32}$/.test(form.username)) {
        showToast('用户名：2-32 位小写字母/数字/_ . -', 'error')
        return
      }
      if (form.password.length < 8) {
        showToast('初始密码至少 8 位', 'error')
        return
      }
    }
    if (form.supervisorId !== '') {
      const sup = candidates.find((c) => c.id === Number(form.supervisorId))
      if (!sup) {
        showToast('主管必须选择在职员工', 'error')
        return
      }
    }
    setSaving(true)
    try {
      if (mode === 'create') {
        const body: UserCreateBody = {
          username: form.username.trim(),
          password: form.password,
          realName: form.realName.trim(),
          gender: form.gender || null,
          position: form.position || null,
          phone: form.phone || null,
          email: form.email || null,
          orgId: form.orgId === '' ? null : Number(form.orgId),
          supervisorId: form.supervisorId === '' ? null : Number(form.supervisorId),
          jobLevelId: form.jobLevelId === '' ? null : Number(form.jobLevelId),
          jobTitleId: form.jobTitleId === '' ? null : Number(form.jobTitleId),
          roleCodes: form.roleCodes,
        }
        await userApi.create(body)
        showToast('员工已创建')
      } else if (initial) {
        const body: UserUpdateBody = {
          realName: form.realName.trim() || null,
          position: form.position || null,
          phone: form.phone || null,
          email: form.email || null,
          gender: form.gender || null,
          orgId: form.orgId === '' ? null : Number(form.orgId),
          clearOrg: form.orgId === '',
          supervisorId: form.supervisorId === '' ? null : Number(form.supervisorId),
          jobLevelId: form.jobLevelId === '' ? null : Number(form.jobLevelId),
          clearJobLevel: form.jobLevelId === '',
          jobTitleId: form.jobTitleId === '' ? null : Number(form.jobTitleId),
          clearJobTitle: form.jobTitleId === '',
          roleCodes: form.roleCodes,
        }
        await userApi.update(initial.id, body)
        showToast('已保存')
      }
      onSaved()
    } catch (e) {
      showToast(e instanceof Error ? e.message : '保存失败', 'error')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
      <div className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white p-6 shadow-xl dark:bg-slate-800">
        <h3 className="mb-4 text-base font-semibold text-slate-800 dark:text-slate-100">
          {mode === 'create' ? '创建新员工' : `编辑员工：${initial?.realName ?? ''}`}
        </h3>
        <div className="grid grid-cols-2 gap-3">
          <div className="col-span-2">
            <label className="mb-1 block text-xs text-slate-500">
              姓名 <span className="text-red-500">*</span>
            </label>
            <input value={form.realName} onChange={(e) => set('realName', e.target.value)} className={inputCls} autoFocus />
          </div>
          <div>
            <label className="mb-1 block text-xs text-slate-500">用户名（创建后不可改）</label>
            <input
              value={form.username}
              disabled={mode === 'edit'}
              onChange={(e) => set('username', e.target.value)}
              placeholder="小写字母/数字/_ . -"
              className={`${inputCls} disabled:opacity-50`}
            />
          </div>
          {mode === 'create' ? (
            <div>
              <label className="mb-1 block text-xs text-slate-500">
                初始密码（≥8 位）<span className="text-red-500">*</span>
              </label>
              <input
                type="password"
                value={form.password}
                onChange={(e) => set('password', e.target.value)}
                className={inputCls}
              />
            </div>
          ) : (
            <div>
              <label className="mb-1 block text-xs text-slate-500">性别</label>
              <select value={form.gender} onChange={(e) => set('gender', e.target.value)} className={inputCls}>
                <option value="">未填</option>
                <option value="male">男</option>
                <option value="female">女</option>
              </select>
            </div>
          )}
          <div>
            <label className="mb-1 block text-xs text-slate-500">部门（唯一；6.4.2 R1）</label>
            <select value={form.orgId} onChange={(e) => set('orgId', e.target.value ? Number(e.target.value) : '')} className={inputCls}>
              <option value="">未分配</option>
              {orgs.map((o) => (
                <option key={o.id} value={o.id}>
                  {o.orgName}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs text-slate-500">主管（仅在职员工）</label>
            <select
              value={form.supervisorId}
              onChange={(e) => set('supervisorId', e.target.value ? Number(e.target.value) : '')}
              className={inputCls}
            >
              <option value="">无</option>
              {candidates.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.realName ?? c.username}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs text-slate-500">岗位</label>
            <input value={form.position} onChange={(e) => set('position', e.target.value)} className={inputCls} />
          </div>
          <div>
            <label className="mb-1 block text-xs text-slate-500">手机</label>
            <input value={form.phone} onChange={(e) => set('phone', e.target.value)} className={inputCls} />
          </div>
          <div>
            <label className="mb-1 block text-xs text-slate-500">邮箱</label>
            <input value={form.email} onChange={(e) => set('email', e.target.value)} className={inputCls} />
          </div>
          <div>
            <label className="mb-1 block text-xs text-slate-500">职级</label>
            <select
              value={form.jobLevelId}
              onChange={(e) => set('jobLevelId', e.target.value ? Number(e.target.value) : '')}
              className={inputCls}
            >
              <option value="">未设置</option>
              {levels.map((l) => (
                <option key={l.id} value={l.id}>
                  {l.name}
                </option>
              ))}
            </select>
          </div>
          <div className="col-span-2">
            <label className="mb-1 block text-xs text-slate-500">职称</label>
            <select
              value={form.jobTitleId}
              onChange={(e) => set('jobTitleId', e.target.value ? Number(e.target.value) : '')}
              className={inputCls}
            >
              <option value="">未设置</option>
              {titles.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
          </div>
          {canManage && (
            <div className="col-span-2">
              <label className="mb-1 block text-xs text-slate-500">角色</label>
              <div className="flex flex-wrap gap-2">
                {roles.map((r) => (
                  <label key={r} className="flex items-center gap-1 text-sm text-slate-600 dark:text-slate-300">
                    <input
                      type="checkbox"
                      checked={form.roleCodes.includes(r)}
                      onChange={(e) =>
                        set(
                          'roleCodes',
                          e.target.checked ? [...form.roleCodes, r] : form.roleCodes.filter((x) => x !== r),
                        )
                      }
                    />
                    {r}
                  </label>
                ))}
              </div>
            </div>
          )}
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <button
            onClick={onClose}
            className="rounded-lg border border-slate-300 px-4 py-2 text-sm text-slate-600 dark:border-slate-600 dark:text-slate-300"
          >
            取消
          </button>
          <button
            onClick={() => void submit()}
            disabled={saving}
            className="rounded-lg bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 disabled:opacity-50"
          >
            {saving ? '保存中…' : '保存'}
          </button>
        </div>
      </div>
    </div>
  )
}
