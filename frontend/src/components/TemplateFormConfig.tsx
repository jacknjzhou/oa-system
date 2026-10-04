import { useMemo, useState } from 'react'
import type { FormField, FormFieldType } from '../types'
import { CONTROLS, CONTROL_GROUPS, controlByType } from './formControls'

interface TemplateFormConfigProps {
  fields: FormField[]
  onChange: (fields: FormField[]) => void
}

/**
 * 表单设计器（P2-1b，对照致碟云三栏布局）：
 * 左：控件库（19 类控件分组）；中：表单画布（增删/排序/选中）；右：属性面板。
 * 所有控件须保存后才可用——未保存的画布改动不生效（与智蝶云一致）。
 */
export default function TemplateFormConfig({ fields, onChange }: TemplateFormConfigProps) {
  const [selectedKey, setSelectedKey] = useState<string | null>(null)

  const selectedIndex = useMemo(
    () => fields.findIndex((f) => f.key === selectedKey),
    [fields, selectedKey]
  )
  const selected = selectedIndex >= 0 ? fields[selectedIndex] : null
  const selectedDef = selected ? controlByType(selected.type) : undefined

  const emit = (next: FormField[]) => {
    onChange(next)
  }

  const addField = (type: FormFieldType) => {
    const def = controlByType(type)
    const count = fields.filter((f) => f.type === type).length
    const field: FormField = {
      key: `${type}${count > 0 ? `_${count + 1}` : ''}`,
      label: def?.defaultLabel || type,
      type,
      required: false,
      ...(def?.hasOptions ? { options: ['选项一', '选项二'] } : {}),
      ...(def?.hasUnit ? { unit: '元' } : {}),
    }
    emit([...fields, field])
    setSelectedKey(field.key)
  }

  const updateField = (key: string, patch: Partial<FormField>) => {
    emit(fields.map((f) => (f.key === key ? { ...f, ...patch } : f)))
  }

  const removeField = (key: string) => {
    if (selectedKey === key) setSelectedKey(null)
    emit(fields.filter((f) => f.key !== key))
  }

  const moveField = (key: string, offset: -1 | 1) => {
    const index = fields.findIndex((f) => f.key === key)
    const target = index + offset
    if (index < 0 || target < 0 || target >= fields.length) return
    const next = [...fields]
    ;[next[index], next[target]] = [next[target], next[index]]
    emit(next)
  }

  return (
    <div className="grid grid-cols-1 gap-3 lg:grid-cols-[180px_1fr_260px]">
      {/* 左栏：控件库 */}
      <div className="rounded-lg border border-slate-200 bg-slate-50/60 p-3 dark:border-slate-600 dark:bg-slate-900/40">
        <h4 className="mb-2 text-xs font-semibold text-slate-500 dark:text-slate-400">控件库</h4>
        <div className="max-h-[420px] space-y-3 overflow-y-auto pr-1">
          {CONTROL_GROUPS.map((group) => (
            <div key={group}>
              <p className="mb-1 text-[11px] text-slate-400 dark:text-slate-500">{group}</p>
              <div className="space-y-1">
                {CONTROLS.filter((c) => c.group === group).map((c) => (
                  <button
                    key={c.type}
                    type="button"
                    className="block w-full rounded border border-dashed border-slate-300 px-2 py-1.5 text-left text-xs text-slate-600 transition-colors hover:border-primary-400 hover:bg-primary-50 hover:text-primary-600 dark:border-slate-600 dark:text-slate-300 dark:hover:border-primary-500 dark:hover:bg-primary-500/10"
                    onClick={() => addField(c.type)}
                  >
                    + {c.name}
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* 中栏：表单画布 */}
      <div className="rounded-lg border border-slate-200 p-3 dark:border-slate-600">
        <h4 className="mb-2 text-xs font-semibold text-slate-500 dark:text-slate-400">
          表单画布（{fields.length} 个控件）
        </h4>
        {fields.length === 0 ? (
          <p className="rounded-lg border border-dashed border-slate-300 px-3 py-8 text-center text-sm text-slate-400 dark:border-slate-600">
            从左侧控件库点击控件添加
          </p>
        ) : (
          <div className="max-h-[420px] space-y-2 overflow-y-auto pr-1">
            {fields.map((field, index) => {
              const def = controlByType(field.type)
              const isSelected = field.key === selectedKey
              return (
                <div
                  key={field.key}
                  className={`flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 transition-colors ${
                    isSelected
                      ? 'border-primary-400 bg-primary-50 dark:border-primary-500 dark:bg-primary-500/10'
                      : 'border-slate-200 hover:border-slate-300 dark:border-slate-600 dark:hover:border-slate-500'
                  }`}
                  onClick={() => setSelectedKey(field.key)}
                >
                  <span className="min-w-0 flex-1 truncate text-sm text-slate-700 dark:text-slate-200">
                    {field.label || field.key}
                    {field.required && <span className="ml-1 text-red-500">*</span>}
                  </span>
                  <span className="shrink-0 rounded bg-slate-100 px-1.5 py-0.5 text-[11px] text-slate-400 dark:bg-slate-700 dark:text-slate-300">
                    {def?.name ?? field.type}
                  </span>
                  <button
                    type="button"
                    className="shrink-0 text-slate-300 hover:text-slate-500 disabled:opacity-30 dark:text-slate-500"
                    disabled={index === 0}
                    title="上移"
                    onClick={(e) => {
                      e.stopPropagation()
                      moveField(field.key, -1)
                    }}
                  >
                    ↑
                  </button>
                  <button
                    type="button"
                    className="shrink-0 text-slate-300 hover:text-slate-500 disabled:opacity-30 dark:text-slate-500"
                    disabled={index === fields.length - 1}
                    title="下移"
                    onClick={(e) => {
                      e.stopPropagation()
                      moveField(field.key, 1)
                    }}
                  >
                    ↓
                  </button>
                  <button
                    type="button"
                    className="shrink-0 text-slate-300 hover:text-red-500"
                    title="删除"
                    onClick={(e) => {
                      e.stopPropagation()
                      removeField(field.key)
                    }}
                  >
                    ✕
                  </button>
                </div>
              )
            })}
          </div>
        )}
      </div>

      {/* 右栏：属性面板 */}
      <div className="rounded-lg border border-slate-200 p-3 dark:border-slate-600">
        <h4 className="mb-2 text-xs font-semibold text-slate-500 dark:text-slate-400">属性</h4>
        {!selected ? (
          <p className="text-xs text-slate-400 dark:text-slate-500">点击画布中的控件编辑属性</p>
        ) : (
          <div className="space-y-3 text-xs">
            <div>
              <label className="font-medium text-slate-500 dark:text-slate-400">控件类型</label>
              <select
                className="input mt-1 py-1.5 text-xs"
                value={selected.type}
                onChange={(e) => updateField(selected.key, { type: e.target.value as FormFieldType })}
              >
                {CONTROLS.map((c) => (
                  <option key={c.type} value={c.type}>
                    {c.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="font-medium text-slate-500 dark:text-slate-400">字段 Key</label>
              <input
                className="input mt-1 py-1.5 text-xs"
                value={selected.key}
                placeholder="如 amount"
                onChange={(e) => {
                  const nextKey = e.target.value.trim()
                  if (!nextKey || nextKey === selected.key) return
                  if (fields.some((f) => f.key === nextKey)) return
                  updateField(selected.key, { key: nextKey })
                  setSelectedKey(nextKey)
                }}
              />
            </div>
            <div>
              <label className="font-medium text-slate-500 dark:text-slate-400">字段名称</label>
              <input
                className="input mt-1 py-1.5 text-xs"
                value={selected.label}
                placeholder="如 金额"
                onChange={(e) => updateField(selected.key, { label: e.target.value })}
              />
            </div>
            <label className="flex cursor-pointer select-none items-center gap-1.5 font-medium text-slate-600 dark:text-slate-300">
              <input
                type="checkbox"
                className="h-3.5 w-3.5 accent-primary-600"
                checked={selected.required}
                onChange={(e) => updateField(selected.key, { required: e.target.checked })}
              />
              必填
            </label>
            <div>
              <label className="font-medium text-slate-500 dark:text-slate-400">占位提示</label>
              <input
                className="input mt-1 py-1.5 text-xs"
                value={selected.placeholder ?? ''}
                placeholder="请输入…"
                onChange={(e) => updateField(selected.key, { placeholder: e.target.value || undefined })}
              />
            </div>
            {selectedDef?.hasOptions && (
              <div>
                <label className="font-medium text-slate-500 dark:text-slate-400">选项（每行一个）</label>
                <textarea
                  className="input mt-1 min-h-[72px] resize-y py-1.5 text-xs"
                  value={(selected.options ?? []).join('\n')}
                  onChange={(e) =>
                    updateField(selected.key, {
                      options: e.target.value.split('\n').map((s) => s.trim()).filter(Boolean),
                    })
                  }
                />
              </div>
            )}
            {selectedDef?.hasUnit && (
              <div>
                <label className="font-medium text-slate-500 dark:text-slate-400">单位</label>
                <input
                  className="input mt-1 py-1.5 text-xs"
                  value={selected.unit ?? ''}
                  placeholder="元"
                  onChange={(e) => updateField(selected.key, { unit: e.target.value || undefined })}
                />
              </div>
            )}
            {(selected.type === 'number' || selected.type === 'amount') && (
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="font-medium text-slate-500 dark:text-slate-400">最小值</label>
                  <input
                    className="input mt-1 py-1.5 text-xs"
                    type="number"
                    value={selected.min ?? ''}
                    onChange={(e) => updateField(selected.key, { min: e.target.value === '' ? undefined : Number(e.target.value) })}
                  />
                </div>
                <div>
                  <label className="font-medium text-slate-500 dark:text-slate-400">最大值</label>
                  <input
                    className="input mt-1 py-1.5 text-xs"
                    type="number"
                    value={selected.max ?? ''}
                    onChange={(e) => updateField(selected.key, { max: e.target.value === '' ? undefined : Number(e.target.value) })}
                  />
                </div>
              </div>
            )}
            {selected.type === 'department' && (
              <p className="rounded bg-amber-50 px-2 py-1.5 text-[11px] text-amber-600 dark:bg-amber-500/10 dark:text-amber-400">
                底座以文本输入承载，部门选择器待员工/部门模块（P4）
              </p>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
