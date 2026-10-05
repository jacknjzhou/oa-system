import AttendanceTabs from '../components/AttendanceTabs'
import EmptyState from '../components/EmptyState'

/** 假期类型（占位，T8 实现完整 CRUD）。 */
export default function LeaveTypes() {
  return (
    <div className="mx-auto max-w-6xl">
      <AttendanceTabs />
      <div className="rounded-2xl border border-slate-200 bg-white p-10 dark:border-slate-700 dark:bg-slate-800">
        <EmptyState title="假期类型管理" description="类型新增/编辑/停用将在这里提供" />
      </div>
    </div>
  )
}
