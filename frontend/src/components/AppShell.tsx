import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useTheme } from './Theme'
import { getStoredUser, logout } from '../api/auth'
import NotificationBell from './NotificationBell'

interface NavItem {
  to: string
  label: string
  icon: string
  /** 需要的权限码（任一匹配即可）；ADMIN 直通 */
  perm?: string | string[]
}

const NAV_ITEMS: NavItem[] = [
  {
    to: '/',
    label: '审批主页',
    icon: 'M3 12l9-9 9 9M5 10v10a1 1 0 001 1h3a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1h3a1 1 0 001-1V10',
  },
  {
    to: '/start',
    label: '创建申请',
    icon: 'M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z',
  },
  {
    to: '/tasks/todo',
    label: '待审批',
    icon: 'M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 9l2 2 4-4',
  },
  {
    to: '/tasks/done',
    label: '已审批',
    icon: 'M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z',
  },
  {
    to: '/my-instances',
    label: '我的申请',
    icon: 'M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z',
  },
  {
    to: '/cc',
    label: '抄送给我',
    icon: 'M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z',
  },
  {
    to: '/attendance',
    label: '考勤&假期',
    icon: 'M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z',
  },
  {
    to: '/system',
    label: '系统',
    icon: 'M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572-1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573 1.066c-1.543-.94-3.31.826-2.37 2.37a1.724 1.724 0 00-1.065 2.572c-.426 1.756-1.756 2.924 0 3.35a1.724 1.724 0 002.573 1.066c.996-.608 2.296.07 2.572-1.065zM15.7 10.3l-3.99 3.99m7.03-9.94-3.99 3.99',
    perm: ['hr:user', 'hr:dept', 'hr:level', 'system:permission', 'system:settings', 'system:log', 'system:company'],
  },
  {
    to: '/approval-types',
    label: '审批类型',
    icon: 'M7 21h10a2 2 0 002-2V9.414a1 1 0 00-.293-.707l-5.414-5.414A1 1 0 0012.586 3H7a2 2 0 00-2 2v14a2 2 0 002 2z',
  },
  {
    to: '/documents',
    label: '公文',
    icon: 'M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z',
  },
  {
    to: '/templates',
    label: '审批模板',
    icon: 'M4 5a1 1 0 011-1h14a1 1 0 011 1v2a1 1 0 01-1 1H4a1 1 0 01-1-1V5zM4 13a1 1 0 011-1h6a1 1 0 011 1v6a1 1 0 01-1 1H4a1 1 0 01-1-1v-6zM16 13a1 1 0 011-1h3a1 1 0 011 1v6a1 1 0 01-1 1h-3a1 1 0 01-1-1v-6z',
  },
]

function pageTitle(pathname: string): string {
  if (pathname.startsWith('/tasks/todo')) return '待审批'
  if (pathname.startsWith('/tasks/done')) return '已审批'
  if (pathname.startsWith('/task/')) return '审批处理'
  if (pathname.startsWith('/tracking/')) return '流程跟踪'
  if (pathname.startsWith('/templates/new')) return '新建模板'
  if (pathname.startsWith('/templates/')) return '编辑模板'
  if (pathname.startsWith('/templates')) return '审批模板'
  if (pathname.startsWith('/my-instances')) return '我的申请'
  if (pathname.startsWith('/approval-types')) return '审批类型'
  if (pathname.startsWith('/cc')) return '抄送给我'
  if (pathname.startsWith('/attendance/all')) return '全部考勤'
  if (pathname.startsWith('/attendance/settings')) return '考勤设置'
  if (pathname.startsWith('/attendance')) return '我的考勤'
  if (pathname.startsWith('/leave/types')) return '假期类型'
  if (pathname.startsWith('/leave/balance/')) return '假期余额'
  if (pathname.startsWith('/leave')) return '假期管理'
  if (pathname.startsWith('/system/users')) return '员工管理'
  if (pathname.startsWith('/system/departments')) return '部门管理'
  if (pathname.startsWith('/system/job-titles')) return '职称管理'
  if (pathname.startsWith('/system/job-levels')) return '职级管理'
  if (pathname.startsWith('/system/groups')) return '权组与权限'
  if (pathname.startsWith('/system/settings')) return '系统设置'
  if (pathname.startsWith('/system')) return '系统'
  if (pathname.startsWith('/users')) return '员工管理'
  if (pathname.startsWith('/permission-groups')) return '权组与权限'
  if (pathname.startsWith('/settings')) return '系统设置'
  if (pathname.startsWith('/documents/')) return '公文详情'
  if (pathname.startsWith('/documents')) return '公文'
  if (pathname === '/') return '审批主页'
  if (pathname.startsWith('/start')) return '创建申请'
  return '发起审批'
}

export default function AppShell() {
  const { theme, toggleTheme } = useTheme()
  const navigate = useNavigate()
  const location = useLocation()
  const user = getStoredUser()
  const title = pageTitle(location.pathname)
  const hasPerm = (perm?: string | string[]) => {
    if (!perm) return true
    if ((user?.roles ?? []).includes('ADMIN')) return true
    const codes = user?.permissions ?? []
    return (Array.isArray(perm) ? perm : [perm]).some((c) => codes.includes(c))
  }

  const handleLogout = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="flex h-screen overflow-hidden bg-slate-100 dark:bg-slate-900">
      {/* 左侧 Sidebar */}
      <aside className="flex w-60 shrink-0 flex-col bg-slate-900 text-slate-200">
        <div className="flex h-16 items-center gap-3 px-5">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-primary-500 to-violet-500 text-lg font-bold text-white shadow-lg">
            OA
          </div>
          <div>
            <p className="text-base font-semibold leading-tight text-white">OA 审批系统</p>
            <p className="text-xs text-slate-400">Flowable 流程引擎</p>
          </div>
        </div>

        <nav className="mt-4 flex-1 space-y-1 overflow-y-auto px-3">
          {NAV_ITEMS.filter((item) => hasPerm(item.perm)).map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) =>
                `flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-primary-600 text-white shadow-md'
                    : 'text-slate-300 hover:bg-slate-800 hover:text-white'
                }`
              }
            >
              <svg
                className="h-5 w-5 shrink-0"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                strokeWidth={1.8}
              >
                <path strokeLinecap="round" strokeLinejoin="round" d={item.icon} />
              </svg>
              {item.label}
            </NavLink>
          ))}
        </nav>

        {/* 底部用户卡片 */}
        <div className="border-t border-slate-800 p-4">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-primary-500/20 text-sm font-semibold text-primary-300 ring-1 ring-primary-500/40">
              {(user?.realName || user?.username || '?').slice(0, 1)}
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-white">
                {user?.realName || user?.username || '未知用户'}
              </p>
              <p className="truncate text-xs text-slate-400">
                {user?.roles?.length ? user.roles.join(' / ') : user?.position || '普通用户'}
              </p>
            </div>
            <button
              type="button"
              onClick={handleLogout}
              title="退出登录"
              className="rounded-lg p-1.5 text-slate-400 transition-colors hover:bg-slate-800 hover:text-red-400"
            >
              <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1"
                />
              </svg>
            </button>
          </div>
        </div>
      </aside>

      {/* 右侧内容区 */}
      <div className="flex min-w-0 flex-1 flex-col">
        {/* 顶栏 */}
        <header className="flex h-16 shrink-0 items-center justify-between border-b border-slate-200 bg-white px-6 dark:border-slate-700 dark:bg-slate-800">
          <div className="flex items-center gap-2 text-sm">
            <span className="text-slate-400 dark:text-slate-500">OA 审批</span>
            <span className="text-slate-300 dark:text-slate-600">/</span>
            <h1 className="font-semibold text-slate-900 dark:text-slate-100">{title}</h1>
          </div>
          <div className="flex items-center gap-3">
            <NotificationBell />
            <button
              type="button"
              onClick={toggleTheme}
              title={theme === 'light' ? '切换到深色模式' : '切换到浅色模式'}
              className="rounded-lg p-2 text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-700 dark:text-slate-400 dark:hover:bg-slate-700 dark:hover:text-slate-200"
            >
              {theme === 'light' ? (
                <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z"
                  />
                </svg>
              ) : (
                <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z"
                  />
                </svg>
              )}
            </button>
            <div className="hidden items-center gap-2 sm:flex">
              <span className="text-sm font-medium text-slate-700 dark:text-slate-200">
                {user?.realName || user?.username || ''}
              </span>
            </div>
          </div>
        </header>

        {/* 路由出口 */}
        <main className="flex-1 overflow-y-auto">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
