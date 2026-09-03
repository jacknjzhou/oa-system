import type { FormField, FormFieldType } from '../types'

interface TemplateFormConfigProps {
  fields: FormField[]
  onChange: (fields: FormField[]) => void
}

const FIELD_TYPES: { value: FormFieldType; label: string }[] = [
  { value: 'text', label: '单行文本' },
  { value: 'textarea', label: '多行文本' },
  { value: 'number', label: '数字' },
  { value: 'select', label: '下拉选择' },
]

/**
 * 模板表单字段配置编辑器：字段增删、排序、属性编辑，
 * 最终序列化为 formConfig JSON 字符串
 */
export default function TemplateFormConfig({ fields, onChange }: TemplateFormConfigProps) {
  const updateField = (index: number, patch: Partial<FormField>) => {
    onChange(fields.map((f, i) => (i === index ? { ...f, ...patch } : f)))
  }

  const addField = () => {
    onChange([
      ...fields,
      { key: `field_${fields.length + 1}`, label: '', type: 'text', required: false },
    ])
  }

  const removeField = (index: number) => {
    onChange(fields.filter((_, i) => i !== index))
  }

  const moveField = (index: number, offset: -1 | 1) => {
    const target = index + offset
    if (target < 0 || target >= fields.length) return
    const next = [...fields]
    ;[next[index], next[target]] = [next[target], next[index]]
    onChange(next)
  }

  return (
    <div className="space-y-3">
      {fields.length === 0 && (
        <p className="rounded-lg bg-slate-50 px-3 py-4 text-center text-sm text-slate-400 dark:bg-slate-900/60 dark:text-slate-500">
          暂无表单字段，点击下方按钮添加
        </p>
      )}

      {fields.map((field, index) => (
        <div key={index} className="rounded-lg border border-slate-200 p-3 dark:border-slate-600">
          <div className="grid grid-cols-2 gap-2">
            <div>
              <label className="text-xs font-medium text-slate-500 dark:text-slate-400">字段 Key</label>
              <input
                className="input mt-1 py-1.5 text-xs"
                value={field.key}
                placeholder="如 amount"
                onChange={(e) => updateField(index, { key: e.target.value.trim() })}
              />
            </div>
            <div>
              <label className="text-xs font-medium text-slate-500 dark:text-slate-400">字段类型</label>
              <select
                className="input mt-1 py-1.5 text-xs"
                value={field.type}
                onChange={(e) => updateField(index, { type: e.target.value as FormFieldType })}
              >
                {FIELD_TYPES.map((t) => (
                  <option key={t.value} value={t.value}>
                    {t.label}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="mt-2 grid grid-cols-[1fr_auto] items-end gap-2">
            <div>
              <label className="text-xs font-medium text-slate-500 dark:text-slate-400">字段名称</label>
              <input
                className="input mt-1 py-1.5 text-xs"
                value={field.label}
                placeholder="如 金额"
                onChange={(e) => updateField(index, { label: e.target.value })}
              />
            </div>
            <label className="flex cursor-pointer select-none items-center gap-1.5 pb-2 text-xs font-medium text-slate-600 dark:text-slate-300">
              <input
                type="checkbox"
                className="h-3.5 w-3.5 rounded border-slate-300 text-primary-600 focus:ring-primary-500 dark:border-slate-600 dark:bg-slate-900"
                checked={field.required}
                onChange={(e) => updateField(index, { required: e.target.checked })}
              />
              必填
            </label>
          </div>

          {field.type === 'select' && (
            <div className="mt-2">
              <label className="text-xs font-medium text-slate-500 dark:text-slate-400">
                选项（英文逗号分隔）
              </label>
              <input
                className="input mt-1 py-1.5 text-xs"
                value={(field.options ?? []).join(',')}
                placeholder="如 差旅费,办公费,招待费"
                onChange={(e) =>
                  updateField(index, {
                    options: e.target.value
                      .split(',')
                      .map((s) => s.trim())
                      .filter(Boolean),
                  })
                }
              />
            </div>
          )}

          <div className="mt-2 flex justify-end gap-1">
            <button
              type="button"
              className="rounded-md p-1 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600 disabled:opacity-30 dark:hover:bg-slate-700 dark:hover:text-slate-200"
              onClick={() => moveField(index, -1)}
              disabled={index === 0}
              title="上移"
            >
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M5 15l7-7 7 7" />
              </svg>
            </button>
            <button
              type="button"
              className="rounded-md p-1 text-slate-400 transition-colors hover:bg-slate-100 hover:text-slate-600 disabled:opacity-30 dark:hover:bg-slate-700 dark:hover:text-slate-200"
              onClick={() => moveField(index, 1)}
              disabled={index === fields.length - 1}
              title="下移"
            >
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M19 9l-7 7-7-7" />
              </svg>
            </button>
            <button
              type="button"
              className="rounded-md p-1 text-slate-400 transition-colors hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-500/10 dark:hover:text-red-400"
              onClick={() => removeField(index)}
              title="删除字段"
            >
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"
                />
              </svg>
            </button>
          </div>
        </div>
      ))}

      <button type="button" className="btn btn-secondary w-full" onClick={addField}>
        <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
        </svg>
        添加字段
      </button>
    </div>
  )
}
