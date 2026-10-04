import type { FormField } from '../types'
import type { UserSummary } from '../types'
import { PROVINCES } from './formControls'

interface DynamicFormProps {
  fields: FormField[]
  values: Record<string, string>
  onChange: (key: string, value: string) => void
  disabled?: boolean
  /** 只读渲染（审批查看 / 预览）：文本展示而非输入控件 */
  readOnly?: boolean
  /** 用户目录（contact 控件选人用） */
  users?: UserSummary[]
  /**
   * 字段级节点权限（P2-3 表单操作权限，来自 flowSpec）：
   * editable 可编辑 / readonly 只读 / hidden 隐藏（不渲染）
   */
  perms?: Record<string, 'editable' | 'readonly' | 'hidden'>
}

/** 复合值拆解：dateRange "a ~ b" / provinceCity "a / b" */
function splitComposite(raw: string, sep: string): [string, string] {
  if (!raw) return ['', '']
  const idx = raw.lastIndexOf(sep)
  if (idx < 0) return [raw, '']
  return [raw.slice(0, idx), raw.slice(idx + sep.length)]
}

/**
 * 动态表单（P2-1b 底座）：按模板 formConfig 渲染 19 类控件。
 * 复合控件（dateRange/provinceCity）以 "a ~ b" / "a / b" 字符串存储，保持 values 扁平。
 */
export default function DynamicForm({ fields, values, onChange, disabled, readOnly, users, perms }: DynamicFormProps) {
  const visible = fields.filter((f) => perms?.[f.key] !== 'hidden')
  if (visible.length === 0) {
    return (
      <p className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-500 dark:bg-slate-900/60 dark:text-slate-400">
        该模板未配置表单字段
      </p>
    )
  }

  const disabledOrRead = disabled || readOnly

  return (
    <div className="space-y-4">
      {visible.map((field) => {
        // 字段级权限优先于全局只读；未设置的字段随全局上下文（发起可编辑/审批只读）
        const fieldReadOnly =
          perms && field.key in perms ? perms[field.key] === 'readonly' : !!readOnly
        return (
          <div key={field.key}>
            <label className="form-label" htmlFor={`form-field-${field.key}`}>
              {field.label || field.key}
              {field.required && <span className="ml-0.5 text-red-500">*</span>}
            </label>
            {fieldReadOnly ? (
              <p className="min-h-[36px] text-sm text-slate-700 dark:text-slate-300">
                {values[field.key] ? renderReadOnly(field) : <span className="text-slate-400">-</span>}
              </p>
            ) : (
              renderControl(field)
            )}
          </div>
        )
      })}
    </div>
  )

  function renderControl(field: FormField) {
    const value = values[field.key] ?? ''
    const placeholder = field.placeholder || `请输入${field.label || field.key}`
    const common = { disabled: disabledOrRead, id: `form-field-${field.key}` }

    switch (field.type) {
      case 'textarea':
      case 'richText':
        return (
          <textarea
            className="input min-h-[88px] resize-y"
            placeholder={field.type === 'richText' ? '请输入内容（底座以多行文本承载）' : placeholder}
            value={value}
            disabled={disabledOrRead}
            onChange={(e) => onChange(field.key, e.target.value)}
          />
        )

      case 'radio':
        return (
          <div className="flex flex-wrap gap-x-5 gap-y-2 py-1">
            {(field.options ?? []).map((opt) => (
              <label key={opt} className="flex cursor-pointer items-center gap-1.5 text-sm text-slate-600 dark:text-slate-300">
                <input
                  type="radio"
                  className="h-4 w-4 accent-primary-600"
                  checked={value === opt}
                  disabled={disabledOrRead}
                  onChange={() => onChange(field.key, opt)}
                />
                <span>{opt}</span>
              </label>
            ))}
            {(field.options ?? []).length === 0 && <span className="text-xs text-slate-400">未配置选项</span>}
          </div>
        )

      case 'checkbox': {
        const selected = value ? value.split(',').map((s) => s.trim()) : []
        return (
          <div className="flex flex-wrap gap-x-5 gap-y-2 py-1">
            {(field.options ?? []).map((opt) => (
              <label key={opt} className="flex cursor-pointer items-center gap-1.5 text-sm text-slate-600 dark:text-slate-300">
                <input
                  type="checkbox"
                  className="h-4 w-4 accent-primary-600"
                  checked={selected.includes(opt)}
                  disabled={disabledOrRead}
                  onChange={(e) => {
                    const next = e.target.checked ? [...selected, opt] : selected.filter((s) => s !== opt)
                    onChange(field.key, next.join(', '))
                  }}
                />
                <span>{opt}</span>
              </label>
            ))}
            {(field.options ?? []).length === 0 && <span className="text-xs text-slate-400">未配置选项</span>}
          </div>
        )
      }

      case 'select':
      case 'contact': {
        const options =
          field.type === 'contact'
            ? (users ?? []).map((u) => `${u.id}|${u.realName || u.username}`)
            : (field.options ?? []).map((o) => `${o}|${o}`)
        return (
          <select
            className="input"
            value={value}
            disabled={disabledOrRead}
            onChange={(e) => onChange(field.key, e.target.value)}
          >
            <option value="">{field.type === 'contact' ? '请选择联系人' : `请选择${field.label || field.key}`}</option>
            {options.map((o) => {
              const [val, label] = o.split('|')
              return (
                <option key={o} value={val}>
                  {label}
                </option>
              )
            })}
          </select>
        )
      }

      case 'image':
        return (
          <div className="flex items-center gap-3">
            <input
              type="file"
              accept="image/*"
              className="block text-sm text-slate-500 file:mr-3 file:rounded-lg file:border-0 file:bg-primary-50 file:px-3 file:py-2 file:text-sm file:font-medium file:text-primary-600 dark:file:bg-primary-500/20 dark:file:text-primary-400"
              disabled={disabledOrRead}
              onChange={(e) => onChange(field.key, e.target.files?.[0]?.name ?? '')}
            />
            {value && <span className="truncate text-xs text-slate-400">已选：{value}</span>}
          </div>
        )

      case 'attachment': {
        const selected = value ? value.split(',').map((s) => s.trim()).filter(Boolean) : []
        return (
          <div className="space-y-1.5">
            <input
              type="file"
              multiple
              className="block text-sm text-slate-500 file:mr-3 file:rounded-lg file:border-0 file:bg-primary-50 file:px-3 file:py-2 file:text-sm file:font-medium file:text-primary-600 dark:file:bg-primary-500/20 dark:file:text-primary-400"
              disabled={disabledOrRead}
              onChange={(e) => {
                const added = Array.from(e.target.files ?? []).map((f) => f.name)
                onChange(field.key, [...selected, ...added].join(', '))
              }}
            />
            {selected.length > 0 && (
              <ul className="space-y-1">
                {selected.map((name, i) => (
                  <li key={`${name}-${i}`} className="flex items-center justify-between rounded bg-slate-50 px-2 py-1 text-xs text-slate-500 dark:bg-slate-900/50 dark:text-slate-400">
                    <span className="truncate">📎 {name}</span>
                    <button
                      type="button"
                      className="ml-2 text-red-400 hover:text-red-500 disabled:opacity-40"
                      disabled={disabledOrRead}
                      onClick={() => onChange(field.key, selected.filter((_, j) => j !== i).join(', '))}
                    >
                      移除
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )
      }

      case 'date':
        return (
          <input type="date" className="input" value={value} disabled={disabledOrRead} onChange={(e) => onChange(field.key, e.target.value)} />
        )

      case 'time':
        return (
          <input type="time" className="input" value={value} disabled={disabledOrRead} onChange={(e) => onChange(field.key, e.target.value)} />
        )

      case 'datetime':
        return (
          <input type="datetime-local" className="input" value={value} disabled={disabledOrRead} onChange={(e) => onChange(field.key, e.target.value)} />
        )

      case 'dateRange': {
        const [start, end] = splitComposite(value, ' ~ ')
        return (
          <div className="flex items-center gap-2">
            <input type="date" className="input flex-1" value={start} disabled={disabledOrRead}
              placeholder="开始" onChange={(e) => onChange(field.key, `${e.target.value} ~ ${end}`)} />
            <span className="text-slate-400">~</span>
            <input type="date" className="input flex-1" value={end} disabled={disabledOrRead}
              placeholder="结束" onChange={(e) => onChange(field.key, `${start} ~ ${e.target.value}`)} />
          </div>
        )
      }

      case 'provinceCity': {
        const [province, city] = splitComposite(value, ' / ')
        const cities = PROVINCES[province] ?? []
        return (
          <div className="flex items-center gap-2">
            <select className="input flex-1" value={province} disabled={disabledOrRead} onChange={(e) => onChange(field.key, `${e.target.value} / `)}>
              <option value="">省</option>
              {Object.keys(PROVINCES).map((p) => (
                <option key={p} value={p}>{p}</option>
              ))}
            </select>
            <select className="input flex-1" value={city} disabled={disabledOrRead || !province} onChange={(e) => onChange(field.key, `${province} / ${e.target.value}`)}>
              <option value="">市</option>
              {cities.map((c) => (
                <option key={c} value={c}>{c}</option>
              ))}
            </select>
          </div>
        )
      }

      case 'department':
        return (
          <input
            type="text"
            className="input"
            placeholder={field.placeholder || '请输入部门名称'}
            value={value}
            disabled={disabledOrRead}
            onChange={(e) => onChange(field.key, e.target.value)}
          />
        )

      case 'number':
      case 'amount':
        return (
          <div className="relative">
            <input
              {...common}
              type="number"
              className="input pr-12"
              placeholder={placeholder}
              min={field.min}
              max={field.max}
              step="any"
              value={value}
              disabled={disabledOrRead}
              onChange={(e) => onChange(field.key, e.target.value)}
            />
            {field.unit && (
              <span className="pointer-events-none absolute inset-y-0 right-3 flex items-center text-xs text-slate-400">
                {field.unit}
              </span>
            )}
          </div>
        )

      case 'phone':
        return (
          <input type="tel" className="input" placeholder={field.placeholder || '请输入电话号码'} value={value} disabled={disabledOrRead} onChange={(e) => onChange(field.key, e.target.value)} />
        )

      case 'idCard':
        return (
          <input type="text" className="input" placeholder={field.placeholder || '请输入身份证号'} value={value} disabled={disabledOrRead} onChange={(e) => onChange(field.key, e.target.value)} />
        )

      case 'text':
      default:
        return (
          <input type="text" className="input" placeholder={placeholder} value={value} disabled={disabledOrRead} onChange={(e) => onChange(field.key, e.target.value)} />
        )
    }
  }

  function renderReadOnly(field: FormField): string {
    const raw = values[field.key] ?? ''
    switch (field.type) {
      case 'contact': {
        if (!raw) return '-'
        const user = users?.find((u) => String(u.id) === String(raw))
        return user ? `${user.realName || user.username}` : raw
      }
      case 'amount':
      case 'number':
        return field.unit ? `${raw} ${field.unit}` : raw
      case 'checkbox':
      case 'attachment':
        return raw
      default:
        return raw
    }
  }
}
