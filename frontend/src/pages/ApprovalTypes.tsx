import { useCallback, useEffect, useState } from 'react'
import type { ApprovalType, Template } from '../types'
import { getApprovalTypes, createApprovalType, updateApprovalType, deleteApprovalType } from '../api/approvalType'
import { getTemplates } from '../api/template'
import Modal from '../components/Modal'
import EmptyState, { ErrorState, LoadingState } from '../components/EmptyState'
import { useToast } from '../components/Toast'

interface FormState {
  code: string
  name: string
  category: string
  icon: string
  description: string
  weight: string
  defId: string
  enabled: boolean
}

const EMPTY_FORM: FormState = {
  code: '',
  name: '',
  category: '',
  icon: '📋',
  description: '',
  weight: '0',
  defId: '',
  enabled: true,
}

/** 审批类型管理（P2-1a）：类型 = 名称 + 分类 + 图标 + 关联流程模板 */
export default function ApprovalTypes() {
  const { showToast } = useToast()
  const [types, setTypes] = useState<ApprovalType[]>([])
  const [templates, setTemplates] = useState<Template[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')

  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<ApprovalType | null>(null)
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [saving, setSaving] = useState(false)

  const load = useCallback(() => {
    setLoading(true)
    setLoadError('')
    Promise.all([getApprovalTypes(), getTemplates().catch(() => [] as Template[])])
      .then(([typeData, templateData]) => {
        setTypes(typeData)
        setTemplates(templateData)
      })
      .catch((err) => setLoadError(err instanceof Error ? err.message : '审批类型加载失败'))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const templateName = (defId: string) =>
    templates.find((t) => String(t.id) === String(defId))?.name ?? '-'

  const openCreate = () => {
    setEditing(null)
    setForm({ ...EMPTY_FORM, defId: templates[0] ? templates[0].id : '' })
    setModalOpen(true)
  }

  const openEdit = (type: ApprovalType) => {
    setEditing(type)
    setForm({
      code: type.code,
      name: type.name,
      category: type.category ?? '',
      icon: type.icon ?? '📋',
      description: type.description ?? '',
      weight: String(type.weight ?? 0),
      defId: type.defId,
      enabled: type.enabled,
    })
    setModalOpen(true)
  }

  const set = (patch: Partial<FormState>) => setForm((prev) => ({ ...prev, ...patch }))

  const handleSave = async () => {
    if (!form.code.trim() || !form.name.trim()) {
      showToast('类型代码与名称必填', 'error')
      return
    }
    if (!form.defId) {
      showToast('请选择关联流程模板', 'error')
      return
    }
    setSaving(true)
    try {
      const payload = {
        code: form.code.trim(),
        name: form.name.trim(),
        category: form.category.trim() || undefined,
        icon: form.icon.trim() || undefined,
        description: form.description.trim() || undefined,
        weight: Number(form.weight) || 0,
        defId: form.defId,
        enabled: form.enabled,
      }
      if (editing) {
        await updateApprovalType(editing.id, payload)
        showToast('类型已更新', 'success')
      } else {
        await createApprovalType(payload)
        showToast('类型已创建', 'success')
      }
      setModalOpen(false)
      load()
    } catch (err) {
      showToast(err instanceof Error ? err.message : '保存失败', 'error')
    } finally {
      setSaving(false)
    }
  }

  const handleToggle = async (type: ApprovalType) => {
    try {
      await updateApprovalType(type.id, {
        code: type.code,
        name: type.name,
        category: type.category ?? undefined,
        icon: type.icon ?? undefined,
        description: type.description ?? undefined,
        weight: type.weight,
        defId: type.defId,
        enabled: !type.enabled,
      })
      showToast(type.enabled ? '类型已停用' : '类型已启用', 'success')
      load()
    } catch (err) {
      showToast(err instanceof Error ? err.message : '操作失败', 'error')
    }
  }

  const handleDelete = async (type: ApprovalType) => {
    if (!window.confirm(`确定删除类型「${type.name}」？`)) return
    try {
      await deleteApprovalType(type.id)
      showToast('类型已删除', 'success')
      load()
    } catch (err) {
      showToast(err instanceof Error ? err.message : '删除失败', 'error')
    }
  }

  return (
    <div className="mx-auto max-w-6xl p-6">
      <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-xl font-bold text-slate-900 dark:text-slate-100">审批类型</h2>
          <p className="mt-0.5 text-sm text-slate-500 dark:text-slate-400">
            发起入口的业务分类；「启用」的类型会出现在发起审批页
          </p>
        </div>
        <button type="button" className="btn btn-primary" onClick={openCreate}>
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
          </svg>
          新建类型
        </button>
      </div>

      {loading ? (
        <LoadingState text="正在加载审批类型…" />
      ) : loadError ? (
        <div className="card">
          <ErrorState message={loadError} />
        </div>
      ) : types.length === 0 ? (
        <div className="card">
          <EmptyState icon="🗂️" title="暂无审批类型" description="点击右上角「新建类型」创建" />
        </div>
      ) : (
        <div className="card overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-200 text-left text-xs text-slate-400 dark:border-slate-700 dark:text-slate-500">
                <th className="px-4 py-3 font-medium">类型</th>
                <th className="px-4 py-3 font-medium">分类</th>
                <th className="px-4 py-3 font-medium">关联流程</th>
                <th className="px-4 py-3 font-medium">权重</th>
                <th className="px-4 py-3 font-medium">状态</th>
                <th className="px-4 py-3 text-right font-medium">操作</th>
              </tr>
            </thead>
            <tbody>
              {types.map((type) => (
                <tr key={type.id} className="border-b border-slate-100 last:border-0 dark:border-slate-700/60">
                  <td className="px-4 py-3">
                    <span className="mr-2 text-lg">{type.icon || '📋'}</span>
                    <span className="font-medium text-slate-800 dark:text-slate-200">{type.name}</span>
                    <span className="ml-2 text-xs text-slate-400">{type.code}</span>
                  </td>
                  <td className="px-4 py-3 text-slate-500 dark:text-slate-400">{type.category || '-'}</td>
                  <td className="px-4 py-3 text-slate-500 dark:text-slate-400">
                    {type.defName || templateName(type.defId)}
                  </td>
                  <td className="px-4 py-3 text-slate-500 dark:text-slate-400">{type.weight ?? 0}</td>
                  <td className="px-4 py-3">
                    <span
                      className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                        type.enabled
                          ? 'bg-emerald-50 text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-400'
                          : 'bg-slate-100 text-slate-500 dark:bg-slate-700 dark:text-slate-400'
                      }`}
                    >
                      {type.enabled ? '启用' : '停用'}
                    </span>
                  </td>
                  <td className="whitespace-nowrap px-4 py-3 text-right">
                    <button
                      type="button"
                      className="text-sm text-primary-600 hover:underline dark:text-primary-400"
                      onClick={() => openEdit(type)}
                    >
                      编辑
                    </button>
                    <span className="mx-2 text-slate-300 dark:text-slate-600">|</span>
                    <button
                      type="button"
                      className="text-sm text-slate-600 hover:underline dark:text-slate-300"
                      onClick={() => handleToggle(type)}
                    >
                      {type.enabled ? '停用' : '启用'}
                    </button>
                    <span className="mx-2 text-slate-300 dark:text-slate-600">|</span>
                    <button
                      type="button"
                      className="text-sm text-red-500 hover:underline"
                      onClick={() => handleDelete(type)}
                    >
                      删除
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Modal
        open={modalOpen}
        title={editing ? `编辑类型：${editing.name}` : '新建审批类型'}
        onClose={() => !saving && setModalOpen(false)}
        footer={
          <>
            <button type="button" className="btn btn-secondary" onClick={() => setModalOpen(false)} disabled={saving}>
              取消
            </button>
            <button type="button" className="btn btn-primary" onClick={handleSave} disabled={saving}>
              {saving ? '保存中…' : '保存'}
            </button>
          </>
        }
      >
        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="form-label">类型代码 <span className="text-red-500">*</span></label>
            <input
              className="input"
              placeholder="如 REIMBURSEMENT"
              value={form.code}
              onChange={(e) => set({ code: e.target.value.toUpperCase().replace(/\s+/g, '_') })}
              disabled={!!editing}
            />
            <p className="mt-1 text-xs text-slate-400">唯一标识，保存后不可修改</p>
          </div>
          <div>
            <label className="form-label">名称 <span className="text-red-500">*</span></label>
            <input
              className="input"
              placeholder="如 报销"
              value={form.name}
              onChange={(e) => set({ name: e.target.value })}
            />
          </div>
          <div>
            <label className="form-label">图标</label>
            <input
              className="input"
              placeholder="emoji，如 🧾"
              value={form.icon}
              onChange={(e) => set({ icon: e.target.value })}
            />
          </div>
          <div>
            <label className="form-label">分类</label>
            <input
              className="input"
              list="type-categories"
              placeholder="人事/财务/考勤/休假…"
              value={form.category}
              onChange={(e) => set({ category: e.target.value })}
            />
            <datalist id="type-categories">
              {['人事', '财务', '考勤', '休假', '日常', '法务', '行政', '其他'].map((c) => (
                <option key={c} value={c} />
              ))}
            </datalist>
          </div>
          <div className="col-span-2">
            <label className="form-label">描述</label>
            <input
              className="input"
              placeholder="如 费用报销审批"
              value={form.description}
              onChange={(e) => set({ description: e.target.value })}
            />
          </div>
          <div>
            <label className="form-label">排序权重（小在前）</label>
            <input
              className="input"
              type="number"
              value={form.weight}
              onChange={(e) => set({ weight: e.target.value })}
            />
          </div>
          <div>
            <label className="form-label">关联流程模板 <span className="text-red-500">*</span></label>
            <select
              className="input"
              value={form.defId}
              onChange={(e) => set({ defId: e.target.value })}
            >
              <option value="">请选择</option>
              {templates.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}（{t.defKey}）v{t.version}
                </option>
              ))}
            </select>
          </div>
          <label className="col-span-2 flex cursor-pointer items-center gap-2 text-sm text-slate-600 dark:text-slate-300">
            <input
              type="checkbox"
              className="h-4 w-4 accent-primary-600"
              checked={form.enabled}
              onChange={(e) => set({ enabled: e.target.checked })}
            />
            启用（出现在发起审批页）
          </label>
        </div>
      </Modal>
    </div>
  )
}
