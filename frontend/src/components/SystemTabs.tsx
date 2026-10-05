import { NavLink } from 'react-router-dom'
import { getStoredUser } from '../api/auth'

/**
 * 系统模块六 Tab（规格 6.3.0）：员工 / 部门 / 职称 / 职级 / 权组 / 设置。
 * 各 Tab 按权限码门控（ADMIN 直通）；无任一权限时不渲染。
 */
interface TabDef {
  to: string
  label: string
  perm?: string | string[]
}

const TABS: TabDef[] = [
  { to: '/system/users', label: '员工', perm: 'hr:user' },
  { to: '/system/departments', label: '部门', perm: 'hr:dept' },
  { to: '/system/job-titles', label: '职称', perm: 'hr:level' },
  { to: '/system/job-levels', label: '职级', perm: 'hr:level' },
  { to: '/system/groups', label: '权组', perm: 'system:permission' },
  { to: '/system/settings', label: '设置', perm: ['system:settings', 'system:log', 'system:company'] },
]

export default function SystemTabs() {
  const user = getStoredUser()
  const hasPerm = (perm?: string | string[]) => {
    if (!perm) return true
    if ((user?.roles ?? []).includes('ADMIN')) return true
    const perms = user?.permissions ?? []
    return Array.isArray(perm) ? perm.some((p) => perms.includes(p)) : perms.includes(perm)
  }
  const visible = TABS.filter((t) => hasPerm(t.perm))
  if (visible.length === 0) return null
  return (
    <div className="mb-6 flex flex-wrap gap-1 rounded-xl border border-slate-200 bg-white p-1 dark:border-slate-700 dark:bg-slate-800">
      {visible.map((t) => (
        <NavLink
          key={t.to}
          to={t.to}
          className={({ isActive }) =>
            `rounded-lg px-4 py-2 text-sm font-medium transition-colors ${
              isActive
                ? 'bg-primary-600 text-white shadow'
                : 'text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-700'
            }`
          }
        >
          {t.label}
        </NavLink>
      ))}
    </div>
  )
}
