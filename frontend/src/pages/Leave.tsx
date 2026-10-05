import { getStoredUser } from '../api/auth'
import AttendanceTabs from '../components/AttendanceTabs'
import LeaveManagement from './LeaveManagement'
import MyLeave from './MyLeave'

/**
 * 假期模块入口（合并裁决①：考勤&假期五 Tab）：
 * leave:manage 权限 → 假期管理（HD-01）；否则 → 我的假期（MyLeave）。
 */
export default function Leave() {
  const user = getStoredUser()
  const hasPerm = (perm: string) => {
    if ((user?.roles ?? []).includes('ADMIN')) return true
    return (user?.permissions ?? []).includes(perm)
  }
  const canManage = hasPerm('leave:manage')
  return (
    <div className="mx-auto max-w-6xl">
      <AttendanceTabs />
      {canManage ? <LeaveManagement /> : <MyLeave />}
    </div>
  )
}
