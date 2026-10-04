import type { FormConfig, FormField } from '../types'

/** 格式化 ISO 日期时间为 YYYY-MM-DD HH:mm */
export function formatDateTime(iso?: string | null): string {
  if (!iso) return '-'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 相对时间：刚刚 / x 分钟前 / x 小时前 / 超过 24 小时回退为日期时间 */
export function formatRelativeTime(iso?: string | null): string {
  if (!iso) return '-'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  const diffMs = Date.now() - d.getTime()
  const minutes = Math.floor(diffMs / 60_000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  return formatDateTime(iso)
}

/** 解析模板 formConfig JSON 字符串 */
export function parseFormConfig(formConfig?: string | null): FormConfig {
  if (!formConfig) return { fields: [] }
  try {
    const parsed = JSON.parse(formConfig) as FormConfig
    if (parsed && Array.isArray(parsed.fields)) {
      return { fields: parsed.fields }
    }
    return { fields: [] }
  } catch {
    return { fields: [] }
  }
}

/** 解析实例 businessData JSON 字符串 */
export function parseBusinessData(businessData?: string | null): Record<string, unknown> {
  if (!businessData) return {}
  try {
    const parsed = JSON.parse(businessData) as Record<string, unknown>
    if (parsed && typeof parsed === 'object') return parsed
    return {}
  } catch {
    return {}
  }
}

/** 模板分类 → emoji 图标 */
const CATEGORY_EMOJI: Record<string, string> = {
  请假: '🏖️',
  报销: '💰',
  采购: '🛒',
  合同: '📜',
  用章: '📇',
  出差: '✈️',
  通用: '📋',
}

export function categoryEmoji(category?: string | null): string {
  if (!category) return '📋'
  return CATEGORY_EMOJI[category] ?? '📋'
}

/** 校验动态表单，返回第一个错误信息（null 表示通过） */
export function validateFormValues(
  fields: FormField[],
  values: Record<string, string>
): string | null {
  const phoneRe = /^(1[3-9]\d{9}|0\d{2,3}-?\d{7,8})$/
  const idCardRe = /^\d{17}[\dXx]$/
  for (const field of fields) {
    const name = field.label || field.key
    const value = (values[field.key] ?? '').trim()
    if (field.required && !value) {
      return `请填写「${name}」`
    }
    if (!value) continue

    if ((field.type === 'number' || field.type === 'amount') && Number.isNaN(Number(value))) {
      return `「${name}」必须是数字`
    }
    if ((field.type === 'number' || field.type === 'amount') && (field.min != null || field.max != null)) {
      const n = Number(value)
      if ((field.min != null && n < field.min) || (field.max != null && n > field.max)) {
        return `「${name}」须在 ${field.min ?? '-∞'} ~ ${field.max ?? '+∞'} 之间`
      }
    }
    if (field.type === 'phone' && !phoneRe.test(value)) {
      return `「${name}」不是有效电话号码`
    }
    if (field.type === 'idCard' && !idCardRe.test(value)) {
      return `「${name}」不是有效身份证号`
    }
    if (field.type === 'dateRange' && !value.includes('~')) {
      return `「${name}」需同时选择起止日期`
    }
    if (field.type === 'provinceCity' && !value.includes('/')) {
      return `「${name}」需同时选择省与市`
    }
  }
  return null
}
