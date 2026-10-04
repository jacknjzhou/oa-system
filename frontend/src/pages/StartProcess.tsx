import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import type { Template, UserSummary } from '../types'
import { getTemplates } from '../api/template'
import { startInstance } from '../api/process'
import { getUsers } from '../api/user'
import { useToast } from '../components/Toast'
import DynamicForm from '../components/DynamicForm'
import EmptyState, { ErrorState, LoadingState } from '../components/EmptyState'
import { categoryEmoji, parseFormConfig, validateFormValues } from '../utils/format'

export default function StartProcess() {
  const navigate = useNavigate()
  const { showToast } = useToast()
  const [templates, setTemplates] = useState<Template[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')

  const [category, setCategory] = useState('全部')

  // 发起抽屉状态
  const [selected, setSelected] = useState<Template | null>(null)
  const [title, setTitle] = useState('')
  const [values, setValues] = useState<Record<string, string>>({})
  const [asDraft, setAsDraft] = useState(false)
  const [ccUserIds, setCcUserIds] = useState<string[]>([])
  const [ccUsers, setCcUsers] = useState<UserSummary[]>([])
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    getTemplates(true)
      .then((data) => {
        if (!cancelled) setTemplates(data)
      })
      .catch((err) => {
        if (!cancelled) setLoadError(err instanceof Error ? err.message : '模板加载失败')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    getUsers()
      .then((data) => {
        if (!cancelled) setCcUsers(data)
      })
      .catch(() => {
        /* 抄送名单加载失败不阻断发起主流程 */
      })
    return () => {
      cancelled = true
    }
  }, [])

  const categories = useMemo(() => {
    const set = new Set(templates.map((t) => t.category).filter(Boolean))
    return ['全部', ...Array.from(set)]
  }, [templates])

  const visibleTemplates = useMemo(
    () => (category === '全部' ? templates : templates.filter((t) => t.category === category)),
    [templates, category]
  )

  const openDrawer = (template: Template) => {
    setSelected(template)
    setTitle('')
    setValues({})
    setAsDraft(false)
    setCcUserIds([])
  }

  const toggleCcUser = (id: string) => {
    setCcUserIds((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]))
  }

  const closeDrawer = () => {
    if (submitting) return
    setSelected(null)
  }

  const handleValueChange = (key: string, value: string) => {
    setValues((prev) => ({ ...prev, [key]: value }))
  }

  const handleSubmit = async () => {
    if (!selected) return
    if (!title.trim()) {
      showToast('请填写申请标题', 'error')
      return
    }
    const fields = parseFormConfig(selected.formConfig).fields
    const error = validateFormValues(fields, values)
    if (error) {
      showToast(error, 'error')
      return
    }
    setSubmitting(true)
    try {
      await startInstance({
        defId: selected.id,
        title: title.trim(),
        businessData: JSON.stringify(values),
        draft: asDraft || undefined,
        ccUserIds: ccUserIds.length > 0 ? ccUserIds : undefined,
      })
      showToast(
        asDraft ? '已存为草稿，可在「我的申请」提交' : `流程发起成功${ccUserIds.length > 0 ? '，已选择抄送人' : ''}`,
        'success'
      )
      setSelected(null)
      navigate('/my-instances')
    } catch (err) {
      showToast(err instanceof Error ? err.message : '发起失败，请稍后重试', 'error')
    } finally {
      setSubmitting(false)
    }
  }

  const drawerFields = selected ? parseFormConfig(selected.formConfig).fields : []

  return (
    <div className="mx-auto max-w-6xl p-6">
      {/* 页头 */}
      <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-xl font-bold text-slate-900 dark:text-slate-100">发起审批</h2>
          <p className="mt-0.5 text-sm text-slate-500 dark:text-slate-400">选择审批模板，填写表单后提交申请</p>
        </div>
        <Link
          to="/my-instances"
          className="inline-flex items-center gap-1 text-sm font-medium text-primary-600 transition-colors hover:text-primary-700 dark:text-primary-400 dark:hover:text-primary-300"
        >
          查看我的申请
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
          </svg>
        </Link>
      </div>

      {/* 分类筛选 */}
      <div className="mb-6 flex flex-wrap gap-2">
        {categories.map((cat) => (
          <button
            key={cat}
            type="button"
            className={`rounded-full px-4 py-1.5 text-sm font-medium transition-colors ${
              category === cat
                ? 'bg-primary-600 text-white shadow-sm'
                : 'bg-white text-slate-600 ring-1 ring-inset ring-slate-200 hover:bg-slate-50 dark:bg-slate-800 dark:text-slate-300 dark:ring-slate-600 dark:hover:bg-slate-700'
            }`}
            onClick={() => setCategory(cat)}
          >
            {cat}
          </button>
        ))}
      </div>

      {/* 内容 */}
      {loading ? (
        <LoadingState text="正在加载审批模板…" />
      ) : loadError ? (
        <ErrorState message={loadError} />
      ) : visibleTemplates.length === 0 ? (
        <div className="card">
          <EmptyState
            icon="🗂️"
            title="暂无可发起的审批模板"
            description="请联系管理员在「审批模板」中发布模板"
          />
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {visibleTemplates.map((template) => {
            const fields = parseFormConfig(template.formConfig).fields
            const preview = fields.map((f) => f.label || f.key).slice(0, 4).join('、')
            return (
              <button
                key={template.id}
                type="button"
                className="card group p-5 text-left transition-all duration-200 hover:-translate-y-0.5 hover:border-primary-300 hover:shadow-lifted dark:hover:border-primary-500/50"
                onClick={() => openDrawer(template)}
              >
                <div className="flex items-start justify-between">
                  <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-primary-50 text-2xl dark:bg-primary-500/10">
                    <span role="img" aria-hidden="true">
                      {categoryEmoji(template.category)}
                    </span>
                  </div>
                  <span className="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-500 dark:bg-slate-700 dark:text-slate-300">
                    v{template.version}
                  </span>
                </div>
                <h3 className="mt-4 text-base font-semibold text-slate-900 group-hover:text-primary-600 dark:text-slate-100 dark:group-hover:text-primary-400">
                  {template.name}
                </h3>
                <p className="mt-1 line-clamp-2 min-h-[40px] text-sm text-slate-500 dark:text-slate-400">
                  {preview ? `表单字段：${preview}` : '暂无表单字段'}
                </p>
                <div className="mt-3 flex items-center justify-between border-t border-slate-100 pt-3 dark:border-slate-700">
                  <span className="text-xs text-slate-400 dark:text-slate-500">
                    分类：{template.category || '未分类'}
                  </span>
                  <span className="inline-flex items-center gap-1 text-sm font-medium text-primary-600 opacity-0 transition-opacity group-hover:opacity-100 dark:text-primary-400">
                    发起申请
                    <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                      <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
                    </svg>
                  </span>
                </div>
              </button>
            )
          })}
        </div>
      )}

      {/* 发起抽屉 */}
      {selected && (
        <div className="fixed inset-0 z-40 flex justify-end" role="dialog" aria-modal="true">
          <div className="absolute inset-0 bg-black/40" onClick={closeDrawer} />
          <div className="relative flex h-full w-full max-w-lg flex-col bg-white shadow-2xl dark:bg-slate-800">
            {/* 抽屉头部 */}
            <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4 dark:border-slate-700">
              <div className="flex items-center gap-3">
                <span className="text-2xl">{categoryEmoji(selected.category)}</span>
                <div>
                  <h3 className="text-base font-semibold text-slate-900 dark:text-slate-100">
                    {selected.name}
                  </h3>
                  <p className="text-xs text-slate-400 dark:text-slate-500">
                    {selected.category} · v{selected.version}
                  </p>
                </div>
              </div>
              <button
                type="button"
                className="rounded-lg p-1.5 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600 dark:hover:bg-slate-700 dark:hover:text-slate-200"
                onClick={closeDrawer}
                aria-label="关闭"
              >
                <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                  <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>

            {/* 抽屉内容 */}
            <div className="flex-1 space-y-5 overflow-y-auto px-6 py-5">
              <div>
                <label className="form-label" htmlFor="instance-title">
                  申请标题 <span className="text-red-500">*</span>
                </label>
                <input
                  id="instance-title"
                  className="input"
                  type="text"
                  placeholder="如：10 月份差旅费报销"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  disabled={submitting}
                />
              </div>
              <DynamicForm
                fields={drawerFields}
                values={values}
                onChange={handleValueChange}
                disabled={submitting}
              />
              <div>
                <span className="form-label">抄送人（可选）</span>
                {ccUsers.length > 0 ? (
                  <div className="max-h-36 space-y-1 overflow-y-auto rounded-lg border border-slate-200 p-2 dark:border-slate-600">
                    {ccUsers.map((u) => (
                      <label
                        key={u.id}
                        className="flex cursor-pointer items-center gap-2 rounded px-2 py-1 text-sm text-slate-600 hover:bg-slate-50 dark:text-slate-300 dark:hover:bg-slate-700"
                      >
                        <input
                          type="checkbox"
                          className="h-4 w-4 accent-primary-600"
                          checked={ccUserIds.includes(u.id)}
                          onChange={() => toggleCcUser(u.id)}
                          disabled={submitting}
                        />
                        <span>
                          {u.realName || u.username}
                          {u.position ? <span className="text-xs text-slate-400">（{u.position}）</span> : null}
                        </span>
                      </label>
                    ))}
                  </div>
                ) : (
                  <p className="text-xs text-slate-400">暂无可选用户</p>
                )}
                <p className="mt-1 text-xs text-slate-400 dark:text-slate-500">
                  流程完成时抄送通知所选人（每流程每人一次）
                </p>
              </div>
              <label className="flex cursor-pointer items-start gap-2 text-sm text-slate-600 dark:text-slate-300">
                <input
                  type="checkbox"
                  className="mt-0.5 h-4 w-4 accent-primary-600"
                  checked={asDraft}
                  onChange={(e) => setAsDraft(e.target.checked)}
                  disabled={submitting}
                />
                <span>
                  先存草稿
                  <span className="block text-xs text-slate-400 dark:text-slate-500">
                    不立即进入审批流程，稍后在「我的申请」中提交
                  </span>
                </span>
              </label>
            </div>

            {/* 抽屉底部 */}
            <div className="flex justify-end gap-2 border-t border-slate-200 px-6 py-4 dark:border-slate-700">
              <button type="button" className="btn btn-secondary" onClick={closeDrawer} disabled={submitting}>
                取消
              </button>
              <button type="button" className="btn btn-primary" onClick={handleSubmit} disabled={submitting}>
                {submitting ? '提交中…' : asDraft ? '存为草稿' : '提交申请'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
