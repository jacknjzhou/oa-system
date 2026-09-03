import type { FormField } from '../types'

interface DynamicFormProps {
  fields: FormField[]
  values: Record<string, string>
  onChange: (key: string, value: string) => void
  disabled?: boolean
}

/**
 * 动态表单：按模板 formConfig 渲染 number / text / textarea / select 字段
 */
export default function DynamicForm({ fields, values, onChange, disabled }: DynamicFormProps) {
  if (fields.length === 0) {
    return (
      <p className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-500 dark:bg-slate-900/60 dark:text-slate-400">
        该模板未配置表单字段
      </p>
    )
  }

  return (
    <div className="space-y-4">
      {fields.map((field) => (
        <div key={field.key}>
          <label className="form-label" htmlFor={`form-field-${field.key}`}>
            {field.label || field.key}
            {field.required && <span className="ml-0.5 text-red-500">*</span>}
          </label>
          {field.type === 'textarea' ? (
            <textarea
              id={`form-field-${field.key}`}
              className="input min-h-[88px] resize-y"
              value={values[field.key] ?? ''}
              disabled={disabled}
              placeholder={`请输入${field.label || field.key}`}
              onChange={(e) => onChange(field.key, e.target.value)}
            />
          ) : field.type === 'select' ? (
            <select
              id={`form-field-${field.key}`}
              className="input"
              value={values[field.key] ?? ''}
              disabled={disabled}
              onChange={(e) => onChange(field.key, e.target.value)}
            >
              <option value="">请选择{field.label || field.key}</option>
              {(field.options ?? []).map((opt) => (
                <option key={opt} value={opt}>
                  {opt}
                </option>
              ))}
            </select>
          ) : (
            <input
              id={`form-field-${field.key}`}
              className="input"
              type={field.type === 'number' ? 'number' : 'text'}
              value={values[field.key] ?? ''}
              disabled={disabled}
              placeholder={`请输入${field.label || field.key}`}
              onChange={(e) => onChange(field.key, e.target.value)}
            />
          )}
        </div>
      ))}
    </div>
  )
}
