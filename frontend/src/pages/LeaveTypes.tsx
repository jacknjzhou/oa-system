import { useCallback, useEffect, useState } from 'react'
import { leaveApi, type LeaveType, type LeaveTypeRequest, type LeaveUnit } from '../api/leave'
import AttendanceTabs from '../components/AttendanceTabs'
import { useToast } from '../components/Toast'
import EmptyState, { ErrorState, LoadingState } from '../components/EmptyState'

const UNIT_LABEL: Record<string, string> = { day: '天', hour: '小时', half_day: '半天' }
const QUOTA_LABEL: Record<string, string> = { fixed: '定量', accrual: '累积', none: '不限额' }
const CATEGORY_LABEL: Record<string, string> = {
  STATUTORY: '法定',
  HOLIDAY: '公司假日',
  ANNUAL: '福利年假',
  SICK: '病假',
  CARE: '照护假',
}

interface TypeForm {
  name: string
  code: string
  category: string
  quotaType: LeaveTypeRequest['quotaType']
  annualQuota: string
  unit: LeaveUnit
  weight: number
}

const emptyForm: TypeForm = {
  name: '',
  code: '',
  category: 'ANNUAL',
  quotaType: 'fixed',
  annualQuota: '0',
  unit: 'day',
  weight: 0,
}

/** 假期类型（HD-04/05/06）：CRUD + 启停用 + 删除影响警告。入口需 leave:manage。 */
export default function LeaveTypes() {
  const { showToast } = useToast()
  const [types, setTypes] = useState<LeaveType[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<LeaveType | null>(null)
  const [form, setForm] = useState<TypeForm>(emptyForm)
  const [saving, setSaving] = useState(false)
  const [deleting, setDeleting] = useState<LeaveType | null>(null)

  const load = useCallback(() => {
    setLoading(true)
    setError('')
    leaveApi
      .listAllTypes()
      .then(setTypes)
      .catch((err) => setError(err instanceof Error ? err.message : '加载失败'))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const openCreate = () => {
    setEditing(null)
    setForm(emptyForm)
    setModalOpen(true)
  }

  const openEdit = (t: LeaveType) => {
    setEditing(t)
    setForm({
      name: t.name,
      code: t.code,
      category: t.category,
      quotaType: t.quotaType,
      annualQuota: String(t.annualQuota),
      unit: t.unit ?? 'day',
      weight: t.weight,
    })
    setModalOpen(true)
  }

  const submit = async () => {
    const annual = Math.round(Number(form.annualQuota))
    if (!form.name.trim()) {
      showToast('请输入类型名称', 'error')
      return
    }
    if (!Number.isFinite(annual) || annual < 0) {
      showToast('年度额度需为不小于 0 的整数', 'error')
      return
    }
    setSaving(true)
    try {
      const body = {
        name: form.name.trim(),
        code: form.code.trim() || null,
        category: form.category,
        quotaType: form.quotaType,
        annualQuota: annual,
        unit: form.unit,
        weight: form.weight,
      }
      if (editing) {
        await leaveApi.updateType(editing.id, body)
        showToast('已保存', 'success')
      } else {
        await leaveApi.createType(body)
        showToast('已创建', 'success')
      }
      setModalOpen(false)
      load()
    } catch (err) {
      showToast(err instanceof Error ? err.message : '保存失败', 'error')
    } finally {
      setSaving(false)
    }
  }

  const toggleEnabled = async (t: LeaveType) => {
    setSaving(true)
    try {
      await leaveApi.updateType(t.id, {
        name: t.name,
        code: t.code,
        category: t.category,
        quotaType: t.quotaType,
        annualQuota: t.annualQuota,
        unit: t.unit ?? 'day',
        weight: t.weight,
        enabled: !t.enabled,
      })
      showToast(t.enabled ? '已停用' : '已启用', 'success')
      load()
    } catch (err) {
      showToast(err instanceof Error ? err.message : '操作失败', 'error')
    } finally {
      setSaving(false)
    }
  }

  const confirmDelete = async () => {
    if (!deleting) return
    setSaving(true)
    try {
      await leaveApi.deleteType(deleting.id)
      showToast(`「${deleting.name}」已删除`, 'success')
      setDeleting(null)
      load()
    } catch (err) {
      showToast(err instanceof Error ? err.message : '删除失败', 'error')
    } finally {
      setSaving(false)
    }
  }

  const inputCls =
    'w-full rounded-lg border border-slate-300 bg-transparent px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-800 dark:text-slate-200'

  return (
    <div className="mx-auto max-w-6xl">
      <AttendanceTabs />
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-slate-400">新增/变更类型后，请假表单的下拉选项将自动同步</p>
        <button
          onClick={openCreate}
          className="rounded-lg bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700"
        >
          + 新增类型
        </button>
      </div>

      <div className="mt-4 overflow-hidden rounded-xl border border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-800">
        {loading && types.length === 0 ? (
          <div className="p-8">
            <LoadingState />
          </div>
        ) : error && types.length === 0 ? (
          <div className="p-8">
            <ErrorState message={error} onRetry={load} />
          </div>
        ) : types.length === 0 ? (
          <div className="p-8">
            <EmptyState title="暂无假期类型" description="点击右上角「新增类型」创建第一个假期类型" />
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-left text-xs uppercase text-slate-400 dark:bg-slate-700/50 dark:text-slate-300">
              <tr>
                <th className="px-4 py-3">代码</th>
                <th className="px-4 py-3">名称</th>
                <th className="px-4 py-3">分类</th>
                <th className="px-4 py-3">额度模式</th>
                <th className="px-4 py-3">年度额度</th>
                <th className="px-4 py-3">单位</th>
                <th className="px-4 py-3">排序</th>
                <th className="px-4 py-3">状态</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 dark:divide-slate-700/60">
              {types.map((t) => (
                <tr
                  key={t.id}
                  className={`hover:bg-slate-50 dark:hover:bg-slate-700/40 ${t.enabled ? '' : 'opacity-60'}`}
                >
                  <td className="px-4 py-2.5 font-mono text-xs text-slate-500 dark:text-slate-400">{t.code}</td>
                  <td className="px-4 py-2.5 font-medium">{t.name}</td>
                  <td className="px-4 py-2.5 text-slate-500 dark:text-slate-400">
                    {CATEGORY_LABEL[t.category] || t.category}
                  </td>
                  <td className="px-4 py-2.5 text-slate-500 dark:text-slate-400">
                    {QUOTA_LABEL[t.quotaType] || t.quotaType}
                  </td>
                  <td className="px-4 py-2.5 tabular-nums text-slate-500 dark:text-slate-400">
                    {t.limited ? `${t.annualQuota}${UNIT_LABEL[t.unit] || ''}` : '—'}
                  </td>
                  <td className="px-4 py-2.5 text-slate-500 dark:text-slate-400">{UNIT_LABEL[t.unit] || t.unit}</td>
                  <td className="px-4 py-2.5 tabular-nums text-slate-400">{t.weight}</td>
                  <td className="px-4 py-2.5">
                    <span
                      className={
                        t.enabled
                          ? 'rounded-full bg-emerald-100 px-2 py-0.5 text-xs text-emerald-700 dark:bg-emerald-500/20 dark:text-emerald-300'
                          : 'rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-500 dark:bg-slate-700 dark:text-slate-400'
                      }
                    >
                      {t.enabled ? '启用' : '停用'}
                    </span>
                  </td>
                  <td className="px-4 py-2.5">
                    <div className="flex flex-wrap justify-end gap-2">
                      <button
                        onClick={() => openEdit(t)}
                        className="rounded-lg border border-slate-300 px-3 py-1 text-xs text-slate-600 hover:bg-slate-100 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-700"
                      >
                        编辑
                      </button>
                      <button
                        onClick={() => toggleEnabled(t)}
                        disabled={saving}
                        className={`rounded-lg border px-3 py-1 text-xs ${
                          t.enabled
                            ? 'border-slate-300 text-slate-500 hover:bg-slate-100 dark:border-slate-600 dark:hover:bg-slate-700'
                            : 'border-emerald-300 text-emerald-600 hover:bg-emerald-50 dark:border-emerald-500/50 dark:text-emerald-400 dark:hover:bg-emerald-500/10'
                        } disabled:opacity-50`}
                      >
                        {t.enabled ? '停用' : '启用'}
                      </button>
                      <button
                        onClick={() => setDeleting(t)}
                        className="rounded-lg border border-rose-300 px-3 py-1 text-xs text-rose-600 hover:bg-rose-50 dark:border-rose-500/50 dark:text-rose-400 dark:hover:bg-rose-500/10"
                      >
                        删除
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* 新建/编辑弹窗 */}
      {modalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl dark:bg-slate-800">
            <h3 className="font-semibold">{editing ? `编辑类型 — ${editing.name}` : '新增假期类型'}</h3>
            <div className="mt-4 grid grid-cols-2 gap-3">
              <label className="col-span-2 text-sm">
                <span className="text-slate-500 dark:text-slate-400">名称 *</span>
                <input
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  className={`mt-1 ${inputCls}`}
                  autoFocus
                />
              </label>
              <label className="col-span-2 text-sm">
                <span className="text-slate-500 dark:text-slate-400">代码（留空=名称）</span>
                <input
                  value={form.code}
                  onChange={(e) => setForm({ ...form, code: e.target.value })}
                  disabled={!!editing}
                  placeholder="如 ANNUAL"
                  className={`mt-1 ${inputCls} disabled:opacity-50`}
                />
              </label>
              <label className="text-sm">
                <span className="text-slate-500 dark:text-slate-400">分类</span>
                <select
                  value={form.category}
                  onChange={(e) => setForm({ ...form, category: e.target.value })}
                  className={`mt-1 ${inputCls}`}
                >
                  {Object.entries(CATEGORY_LABEL).map(([v, l]) => (
                    <option key={v} value={v}>
                      {l}
                    </option>
                  ))}
                </select>
              </label>
              <label className="text-sm">
                <span className="text-slate-500 dark:text-slate-400">额度模式</span>
                <select
                  value={form.quotaType}
                  onChange={(e) => setForm({ ...form, quotaType: e.target.value as LeaveTypeRequest['quotaType'] & string })}
                  className={`mt-1 ${inputCls}`}
                >
                  {Object.entries(QUOTA_LABEL).map(([v, l]) => (
                    <option key={v} value={v}>
                      {l}
                    </option>
                  ))}
                </select>
              </label>
              <label className="text-sm">
                <span className="text-slate-500 dark:text-slate-400">年度额度（整数）</span>
                <input
                  type="number"
                  min={0}
                  step={1}
                  value={form.annualQuota}
                  disabled={form.quotaType === 'none'}
                  onChange={(e) => setForm({ ...form, annualQuota: e.target.value })}
                  className={`mt-1 ${inputCls} disabled:opacity-50`}
                />
              </label>
              <label className="text-sm">
                <span className="text-slate-500 dark:text-slate-400">时长单位</span>
                <select
                  value={form.unit}
                  onChange={(e) => setForm({ ...form, unit: e.target.value as LeaveUnit })}
                  disabled={!!editing}
                  className={`mt-1 ${inputCls} disabled:opacity-50`}
                >
                  {Object.entries(UNIT_LABEL).map(([v, l]) => (
                    <option key={v} value={v}>
                      {l}
                    </option>
                  ))}
                </select>
              </label>
              <label className="col-span-2 text-sm">
                <span className="text-slate-500 dark:text-slate-400">排序权重（大者靠前）</span>
                <input
                  type="number"
                  value={form.weight}
                  onChange={(e) => setForm({ ...form, weight: Number(e.target.value) })}
                  className={`mt-1 ${inputCls}`}
                />
              </label>
            </div>
            {editing && (
              <p className="mt-3 text-xs text-amber-600 dark:text-amber-400">
                编辑中：代码与时长单位不可变更（避免破坏余额与历史流水）
              </p>
            )}
            <div className="mt-5 flex justify-end gap-2">
              <button
                onClick={() => setModalOpen(false)}
                className="rounded-lg border border-slate-300 px-4 py-2 text-sm text-slate-600 hover:bg-slate-50 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-700"
              >
                取消
              </button>
              <button
                onClick={submit}
                disabled={saving}
                className="rounded-lg bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 disabled:opacity-50"
              >
                {saving ? '保存中…' : '保存'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 删除确认弹窗（HD-06 影响提示） */}
      {deleting && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl dark:bg-slate-800">
            <h3 className="font-semibold">删除「{deleting.name}」？</h3>
            <p className="mt-3 text-sm text-slate-500 dark:text-slate-400">
              删除后该类型的<b>已有余额与历史流水记录保留</b>，
              但<b>请假表单的下拉选项将不再出现该类型</b>，无法再发起该类型的请假。
            </p>
            <div className="mt-5 flex justify-end gap-2">
              <button
                onClick={() => setDeleting(null)}
                className="rounded-lg border border-slate-300 px-4 py-2 text-sm text-slate-600 hover:bg-slate-50 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-700"
              >
                取消
              </button>
              <button
                onClick={confirmDelete}
                disabled={saving}
                className="rounded-lg bg-rose-500 px-4 py-2 text-sm font-medium text-white hover:bg-rose-600 disabled:opacity-50"
              >
                {saving ? '删除中…' : '确认删除'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
