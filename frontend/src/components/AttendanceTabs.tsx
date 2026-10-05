import { NavLink } from 'react-router-dom'
import { getStoredUser } from '../api/auth'

/**
 * 考勤 & 假期 五 Tab 导航（合并裁决①，规格 5.6-⑤）：
 * 我的考勤 / 全部考勤 / 考勤设置 / 假期管理 / 假期类型。
 * 「全部考勤/考勤设置」所有登录用户可见；「假期类型」需 leave:manage。
 */
interface TabDef {
  to: string
  label: string
  end?: boolean
  perm?: string
}

const TABS: TabDef[] = [
  { to: '/attendance', label: '我的考勤', end: true },
  { to: '/attendance/all', label: '全部考勤' },
  { to: '/attendance/settings', label: '考勤设置' },
  { to: '/leave', label: '假期管理', end: true },
  { to: '/leave/types', label: '假期类型', perm: 'leave:manage' },
]

export default function AttendanceTabs() {
  const user = getStoredUser()
  const hasPerm = (perm?: string) => {
    if (!perm) return true
    if ((user?.roles ?? []).includes('ADMIN')) return true
    return (user?.permissions ?? []).includes(perm)
  }
  return (
    <div className="mb-6 flex flex-wrap gap-1 rounded-xl border border-slate-200 bg-white p-1 dark:border-slate-700 dark:bg-slate-800">
      {TABS.filter((t) => hasPerm(t.perm)).map((t) => (
        <NavLink
          key={t.to}
          to={t.to}
          end={t.end}
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
