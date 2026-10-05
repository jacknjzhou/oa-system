import AttendanceTabs from '../components/AttendanceTabs'
import MyLeave from './MyLeave'

/**
 * 假期模块入口（合并裁决①：考勤&假期五 Tab）。
 * T6 恒渲染 MyLeave；T7 起按 leave:manage 权限切换 LeaveManagement。
 */
export default function Leave() {
  return (
    <div className="mx-auto max-w-6xl">
      <AttendanceTabs />
      <MyLeave />
    </div>
  )
}
