import type { FormFieldType } from '../types'

/** 控件库定义（P2-1b）：19 类控件，按分组组织 */
export interface ControlDef {
  type: FormFieldType
  /** 控件名称（控件库显示） */
  name: string
  group: '基础输入' | '选择' | '文件' | '时间' | '关联' | '专项'
  /** 拖入画布时的默认 label */
  defaultLabel: string
  hasOptions?: boolean
  hasUnit?: boolean
  hasRange?: boolean
}

export const CONTROLS: ControlDef[] = [
  { type: 'text', name: '单行输入框', group: '基础输入', defaultLabel: '文本' },
  { type: 'textarea', name: '多行输入框', group: '基础输入', defaultLabel: '文本' },
  { type: 'richText', name: '富文本输入框', group: '基础输入', defaultLabel: '内容' },
  { type: 'radio', name: '单选框', group: '选择', defaultLabel: '单选', hasOptions: true },
  { type: 'checkbox', name: '多选框', group: '选择', defaultLabel: '多选', hasOptions: true },
  { type: 'select', name: '下拉框', group: '选择', defaultLabel: '下拉选择', hasOptions: true },
  { type: 'image', name: '图片', group: '文件', defaultLabel: '图片' },
  { type: 'attachment', name: '附件', group: '文件', defaultLabel: '附件' },
  { type: 'date', name: '日期', group: '时间', defaultLabel: '日期' },
  { type: 'time', name: '时间', group: '时间', defaultLabel: '时间' },
  { type: 'dateRange', name: '日期区间', group: '时间', defaultLabel: '日期区间', hasRange: true },
  { type: 'datetime', name: '日期时间', group: '时间', defaultLabel: '日期时间' },
  { type: 'contact', name: '联系人', group: '关联', defaultLabel: '联系人' },
  { type: 'department', name: '部门', group: '关联', defaultLabel: '部门' },
  { type: 'amount', name: '金额', group: '专项', defaultLabel: '金额', hasUnit: true },
  { type: 'number', name: '数字', group: '专项', defaultLabel: '数字', hasRange: true },
  { type: 'phone', name: '电话', group: '专项', defaultLabel: '电话' },
  { type: 'idCard', name: '身份证', group: '专项', defaultLabel: '身份证号' },
  { type: 'provinceCity', name: '省市', group: '专项', defaultLabel: '省市', hasRange: true },
]

export const CONTROL_GROUPS: ControlDef['group'][] = ['基础输入', '选择', '文件', '时间', '关联', '专项']

export const controlByType = (type: FormFieldType): ControlDef | undefined =>
  CONTROLS.find((c) => c.type === type)

/** 省市联动静态数据（底座内置；生产可换字典接口） */
export const PROVINCES: Record<string, string[]> = {
  北京市: ['东城区', '西城区', '朝阳区', '海淀区', '丰台区'],
  上海市: ['黄浦区', '徐汇区', '浦东新区', '闵行区'],
  天津市: ['和平区', '南开区', '河西区'],
  重庆市: ['渝中区', '江北区', '南岸区'],
  广东省: ['广州', '深圳', '东莞', '佛山', '珠海'],
  江苏省: ['南京', '苏州', '无锡', '常州'],
  浙江省: ['杭州', '宁波', '温州'],
  四川省: ['成都', '绵阳', '德阳'],
  山东省: ['济南', '青岛', '烟台'],
  河南省: ['郑州', '洛阳', '开封'],
  湖北省: ['武汉', '宜昌', '襄阳'],
  福建省: ['福州', '厦门', '泉州'],
}
