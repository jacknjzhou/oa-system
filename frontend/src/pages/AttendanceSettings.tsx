import EmptyState from '../components/EmptyState'

/** 考勤设置（占位）：上下班时间/迟到规则等规划中。 */
export default function AttendanceSettings() {
  return (
    <div className="mx-auto max-w-3xl">
      <div className="rounded-2xl border border-slate-200 bg-white p-10 dark:border-slate-700 dark:bg-slate-800">
        <EmptyState
          title="考勤设置规划中"
          description="上下班时间、迟到/早退规则、节假日日历等设置项将在考勤模块增强中提供"
        />
      </div>
    </div>
  )
}
