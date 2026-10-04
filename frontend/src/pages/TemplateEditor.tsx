import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import Modeler from 'bpmn-js/lib/Modeler'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css'
import type { FormField, FlowNodeSpec, Role, Template, UserSummary } from '../types'
import { createTemplate, getTemplate, publishTemplate, updateTemplate } from '../api/template'
import { getRoles, getUsers } from '../api/user'
import { buildInitialBpmnXml, flowableModdleDescriptor, syncProcessId } from '../utils/bpmn'
import { buildFlowBpmnXml } from '../utils/bpmnXml'
import { useToast } from '../components/Toast'
import TemplateFormConfig from '../components/TemplateFormConfig'
import FlowDesigner from '../components/FlowDesigner'
import { LoadingState } from '../components/EmptyState'

interface TemplateEditorProps {
  mode: 'new' | 'edit'
}

interface PanelState {
  id: string
  type: string
  isUserTask: boolean
  isSequenceFlow: boolean
  name: string
  groups: string[]
  condition: string
}

const TYPE_LABELS: Record<string, string> = {
  'bpmn:UserTask': '用户任务',
  'bpmn:StartEvent': '开始事件',
  'bpmn:EndEvent': '结束事件',
  'bpmn:ExclusiveGateway': '排他网关',
  'bpmn:InclusiveGateway': '包容网关',
  'bpmn:ParallelGateway': '并行网关',
  'bpmn:SequenceFlow': '顺序流（连线）',
  'bpmn:Process': '流程',
}

export default function TemplateEditor({ mode }: TemplateEditorProps) {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { showToast } = useToast()

  const [template, setTemplate] = useState<Template | null>(null)
  const [defKey, setDefKey] = useState('')
  const [name, setName] = useState('')
  const [category, setCategory] = useState('')
  const [fields, setFields] = useState<FormField[]>([])
  const [roles, setRoles] = useState<Role[]>([])
  const [users, setUsers] = useState<UserSummary[]>([])
  const [flowNodes, setFlowNodes] = useState<FlowNodeSpec[]>([])
  const [initiatorPerms, setInitiatorPerms] = useState<Record<string, 'editable' | 'readonly' | 'hidden'>>({})
  /** 流程来源：可视化设计生成 / 高级 bpmn-js 手绘 */
  const [flowSource, setFlowSource] = useState<'visual' | 'bpmn'>('visual')
  const [loading, setLoading] = useState(mode === 'edit')
  const [loadError, setLoadError] = useState('')
  const [saving, setSaving] = useState(false)
  const [tab, setTab] = useState<'props' | 'form' | 'flow'>('form')
  const [selectedElementId, setSelectedElementId] = useState<string | null>(null)
  const [panel, setPanel] = useState<PanelState | null>(null)
  const [xmlToImport, setXmlToImport] = useState<string | null>(
    mode === 'new' ? buildInitialBpmnXml('new_process', '') : null
  )

  // bpmn-js 的 get() 服务定位为动态字符串，类型系统无法表达，使用 any
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const modelerRef = useRef<any>(null)
  const containerRef = useRef<HTMLDivElement>(null)

  // 初始化 Modeler（一次）
  useEffect(() => {
    if (!containerRef.current || modelerRef.current) return
    const modeler = new Modeler({
      container: containerRef.current,
      moddleExtensions: { flowable: flowableModdleDescriptor },
    })
    modelerRef.current = modeler
    modeler.on('selection.changed', (event: { newSelection?: Array<{ id: string }> }) => {
      const element = (event.newSelection ?? [])[0]
      setSelectedElementId(element ? element.id : null)
    })
    return () => {
      modeler.destroy()
      modelerRef.current = null
    }
  }, [])

  // 编辑模式：加载模板详情
  useEffect(() => {
    if (mode !== 'edit' || !id) return
    let cancelled = false
    setLoading(true)
    setLoadError('')
    getTemplate(id)
      .then((data) => {
        if (cancelled) return
        setTemplate(data)
        setDefKey(data.defKey)
        setName(data.name)
        setCategory(data.category || '')
        try {
          const parsed = JSON.parse(data.formConfig || '{}') as { fields?: FormField[] }
          setFields(Array.isArray(parsed.fields) ? parsed.fields : [])
        } catch {
          setFields([])
        }
        setXmlToImport(data.bpmnXml || buildInitialBpmnXml(data.defKey, data.name))
        if (data.flowSpec) {
          try {
            const parsed = JSON.parse(data.flowSpec) as {
              nodes?: FlowNodeSpec[]
              initiatorPerms?: Record<string, 'editable' | 'readonly' | 'hidden'>
            }
            if (Array.isArray(parsed.nodes) && parsed.nodes.length > 0) {
              setFlowNodes(parsed.nodes)
            }
            if (parsed.initiatorPerms) {
              setInitiatorPerms(parsed.initiatorPerms)
            }
          } catch {
            // flowSpec 损坏时回退高级模式
          }
        }
      })
      .catch((err) => {
        if (!cancelled) setLoadError(err instanceof Error ? err.message : '模板加载失败')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [mode, id])

  // 加载角色与用户列表（bpmn-js 候选角色 / 可视化流程设计器）
  useEffect(() => {
    getRoles()
      .then(setRoles)
      .catch(() => {
        // 角色加载失败不阻塞设计器，仅候选角色不可选
      })
    getUsers()
      .then(setUsers)
      .catch(() => {
        // 用户加载失败不阻塞设计器
      })
  }, [])

  // 导入 XML 到画布
  useEffect(() => {
    const modeler = modelerRef.current
    if (!modeler || !xmlToImport) return
    modeler
      .importXML(xmlToImport)
      .then(() => {
        modeler.get('canvas').zoom('fit-viewport', 'auto')
      })
      .catch((err: Error) => {
        showToast(`流程图导入失败：${err.message}`, 'error')
      })
  }, [xmlToImport, showToast])

  // 选中元素变化时同步属性面板
  useEffect(() => {
    const modeler = modelerRef.current
    if (!modeler || !selectedElementId) {
      setPanel(null)
      return
    }
    const elementRegistry = modeler.get('elementRegistry')
    const element = elementRegistry.get(selectedElementId)
    if (!element || !element.businessObject) {
      setPanel(null)
      return
    }
    const bo = element.businessObject
    const groups = (bo.get('flowable:candidateGroups') || '')
      .toString()
      .split(',')
      .map((s: string) => s.trim())
      .filter(Boolean)
    setPanel({
      id: element.id,
      type: bo.$type || '',
      isUserTask: bo.$type === 'bpmn:UserTask',
      isSequenceFlow: bo.$type === 'bpmn:SequenceFlow',
      name: bo.name || '',
      groups,
      condition: bo.conditionExpression?.body || '',
    })
  }, [selectedElementId])

  /** 更新选中元素的 BPMN 属性 */
  const updateElementProps = (props: Record<string, unknown>) => {
    const modeler = modelerRef.current
    if (!modeler || !selectedElementId) return
    const elementRegistry = modeler.get('elementRegistry')
    const element = elementRegistry.get(selectedElementId)
    if (!element) return
    modeler.get('modeling').updateProperties(element, props)
  }

  const handleNameChange = (value: string) => {
    if (!panel) return
    setPanel({ ...panel, name: value })
    updateElementProps({ name: value })
  }

  const toggleGroup = (code: string) => {
    if (!panel) return
    const groups = panel.groups.includes(code)
      ? panel.groups.filter((g) => g !== code)
      : [...panel.groups, code]
    setPanel({ ...panel, groups })
    updateElementProps({
      'flowable:candidateGroups': groups.length > 0 ? groups.join(',') : undefined,
    })
  }

  const handleConditionChange = (value: string) => {
    if (!panel) return
    setPanel({ ...panel, condition: value })
    const modeler = modelerRef.current
    if (!modeler || !selectedElementId) return
    const elementRegistry = modeler.get('elementRegistry')
    const element = elementRegistry.get(selectedElementId)
    if (!element) return
    const moddle = modeler.get('moddle')
    const trimmed = value.trim()
    updateElementProps({
      conditionExpression: trimmed
        ? moddle.create('bpmn:FormalExpression', { body: trimmed })
        : undefined,
    })
  }

  const validate = (): string | null => {
    if (!name.trim()) return '请填写模板名称'
    const key = defKey.trim()
    if (!/^[a-zA-Z][a-zA-Z0-9_-]*$/.test(key)) {
      return '模板 Key 必须以字母开头，仅可包含字母、数字、下划线和中划线'
    }
    if (fields.some((f) => !f.key.trim())) return '表单字段的 Key 不能为空'
    const keys = fields.map((f) => f.key.trim())
    if (new Set(keys).size !== keys.length) return '表单字段的 Key 不能重复'
    return null
  }

  const handleSave = async (publish: boolean) => {
    const modeler = modelerRef.current
    if (!modeler) return
    const invalid = validate()
    if (invalid) {
      showToast(invalid, 'error')
      return
    }
    setSaving(true)
    try {
      const { xml } = await modeler.saveXML({ format: true })
      // process id 必须等于 defKey（后端按 key 发起流程）
      const modelerXml = syncProcessId(xml || '', defKey.trim())
      const flowSpecJson = JSON.stringify({
        nodes: flowNodes,
        ...(Object.keys(initiatorPerms).length > 0 ? { initiatorPerms } : {}),
      })
      // 流程来源：可视化设计有节点 → 用生成 XML；否则用 bpmn-js 画布 XML
      const finalXml =
        flowSource === 'visual' && flowNodes.length > 0
          ? buildFlowBpmnXml(defKey.trim(), name.trim(), { nodes: flowNodes })
          : modelerXml
      const formConfigJson = JSON.stringify({
        fields: fields.map((f) => ({
          key: f.key.trim(),
          label: f.label,
          type: f.type,
          required: f.required,
          ...(f.type === 'select' ? { options: f.options ?? [] } : {}),
        })),
      })

      if (mode === 'new') {
        const created = await createTemplate({
          defKey: defKey.trim(),
          name: name.trim(),
          category: category.trim(),
          formConfig: formConfigJson,
          bpmnXml: finalXml,
          flowSpec: flowSpecJson,
        })
        if (publish) {
          await publishTemplate(created.id)
        }
        showToast(publish ? '模板已创建并发布' : '模板创建成功', 'success')
      } else if (template) {
        await updateTemplate(template.id, {
          name: name.trim(),
          category: category.trim(),
          formConfig: formConfigJson,
          bpmnXml: finalXml,
          flowSpec: flowSpecJson,
        })
        if (publish) {
          await publishTemplate(template.id)
        }
        showToast(publish ? '模板已保存并发布' : '模板保存成功', 'success')
      }
      navigate('/templates')
    } catch (err) {
      showToast(err instanceof Error ? err.message : '保存失败，请稍后重试', 'error')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="flex h-full flex-col">
      {/* 顶部工具栏 */}
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2 border-b border-slate-200 bg-white px-4 py-3 dark:border-slate-700 dark:bg-slate-800">
        <button
          type="button"
          className="btn btn-secondary"
          onClick={() => navigate('/templates')}
          disabled={saving}
        >
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
          </svg>
          返回
        </button>

        <div className="flex flex-wrap items-center gap-3">
          {mode === 'new' ? (
            <div>
              <label className="mb-0.5 block text-xs font-medium text-slate-500 dark:text-slate-400">
                模板 Key
              </label>
              <input
                className="input w-44 py-1.5"
                value={defKey}
                placeholder="如 expense_claim"
                disabled={loading || saving}
                onChange={(e) => setDefKey(e.target.value)}
              />
            </div>
          ) : (
            <div>
              <label className="mb-0.5 block text-xs font-medium text-slate-500 dark:text-slate-400">
                模板 Key（不可修改）
              </label>
              <code className="inline-block rounded-lg border border-slate-200 bg-slate-50 px-3 py-1.5 text-sm text-slate-600 dark:border-slate-600 dark:bg-slate-900 dark:text-slate-300">
                {template?.defKey || defKey || '-'}
              </code>
            </div>
          )}
          <div>
            <label className="mb-0.5 block text-xs font-medium text-slate-500 dark:text-slate-400">
              模板名称 <span className="text-red-500">*</span>
            </label>
            <input
              className="input w-48 py-1.5"
              value={name}
              placeholder="如 差旅费报销"
              disabled={loading || saving}
              onChange={(e) => setName(e.target.value)}
            />
          </div>
          <div>
            <label className="mb-0.5 block text-xs font-medium text-slate-500 dark:text-slate-400">
              分类
            </label>
            <input
              className="input w-32 py-1.5"
              value={category}
              placeholder="如 报销"
              disabled={loading || saving}
              onChange={(e) => setCategory(e.target.value)}
            />
          </div>
        </div>

        <div className="ml-auto flex gap-2">
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => handleSave(false)}
            disabled={loading || saving}
          >
            保存
          </button>
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => handleSave(true)}
            disabled={loading || saving}
          >
            保存并发布
          </button>
        </div>
      </div>

      {/* 主体：左画布 + 右属性面板 */}
      <div className="flex min-h-0 flex-1">
        <div className="relative min-w-0 flex-1 bg-white dark:bg-slate-800">
          <div ref={containerRef} className="h-full w-full" />
          {tab === 'flow' && flowSource === 'visual' && (
            <div className="absolute inset-0 z-20 bg-white dark:bg-slate-800">
              <FlowDesigner
                spec={flowNodes}
                onChange={setFlowNodes}
                roles={roles}
                users={users}
                fields={fields}
                initiatorPerms={initiatorPerms}
                onInitiatorPermsChange={setInitiatorPerms}
              />
            </div>
          )}
          {tab === 'flow' && flowSource === 'bpmn' && (
            <div className="pointer-events-none absolute left-3 top-3 z-20 rounded bg-slate-100 px-2.5 py-1 text-xs text-slate-500 dark:bg-slate-900 dark:text-slate-400">
              高级模式：以 bpmn-js 手绘内容为准
            </div>
          )}
          {(loading || loadError) && (
            <div className="absolute inset-0 z-10 flex items-center justify-center bg-white/90 dark:bg-slate-800/90">
              {loadError ? (
                <div className="px-6 text-center">
                  <p className="text-sm font-medium text-red-500">{loadError}</p>
                  <button
                    type="button"
                    className="btn btn-secondary mt-3"
                    onClick={() => navigate('/templates')}
                  >
                    返回列表
                  </button>
                </div>
              ) : (
                <LoadingState text="正在加载模板…" />
              )}
            </div>
          )}
        </div>

        {/* 右侧属性面板 */}
        <div className="flex w-80 shrink-0 flex-col border-l border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-800">
          <div className="flex border-b border-slate-200 dark:border-slate-700">
            <button
              type="button"
              className={`flex-1 px-4 py-2.5 text-sm font-medium transition-colors ${
                tab === 'props'
                  ? 'border-b-2 border-primary-600 text-primary-600 dark:text-primary-400'
                  : 'text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200'
              }`}
              onClick={() => setTab('props')}
            >
              节点属性
            </button>
            <button
              type="button"
              className={`flex-1 px-4 py-2.5 text-sm font-medium transition-colors ${
                tab === 'flow'
                  ? 'border-b-2 border-primary-600 text-primary-600 dark:text-primary-400'
                  : 'text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200'
              }`}
              onClick={() => setTab('flow')}
            >
              流程设计
            </button>
            <button
              type="button"
              className={`flex-1 px-4 py-2.5 text-sm font-medium transition-colors ${
                tab === 'form'
                  ? 'border-b-2 border-primary-600 text-primary-600 dark:text-primary-400'
                  : 'text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200'
              }`}
              onClick={() => setTab('form')}
            >
              表单配置
            </button>
          </div>



          <div className="flex-1 overflow-y-auto p-4">
            {tab === 'flow' ? (
              <div className="space-y-4">
                <div>
                  <label className="form-label">流程来源</label>
                  <select
                    className="input"
                    value={flowSource}
                    onChange={(e) => setFlowSource(e.target.value as 'visual' | 'bpmn')}
                  >
                    <option value="visual">可视化设计（自动生成流程图）</option>
                    <option value="bpmn">高级：bpmn-js 手绘</option>
                  </select>
                </div>
                <p className="rounded-lg bg-slate-50 p-3 text-xs leading-relaxed text-slate-500 dark:bg-slate-900/60 dark:text-slate-400">
                  {flowSource === 'visual'
                    ? flowNodes.length > 0
                      ? `已设计 ${flowNodes.length} 个审批节点（在左侧画布区编辑），保存时将自动生成流程图。`
                      : '尚未设计流程：模板可保存与发布，但无法发起流程实例。在左侧画布区添加审批节点。'
                    : '保存时将使用 bpmn-js 画布中的手绘流程图。'}
                </p>
              </div>
            ) : tab === 'form' ? (
              <TemplateFormConfig fields={fields} onChange={setFields} />
            ) : !panel ? (
              <div className="flex flex-col items-center justify-center px-4 py-16 text-center">
                <span className="mb-3 text-4xl" role="img" aria-hidden="true">
                  👆
                </span>
                <p className="text-sm font-semibold text-slate-700 dark:text-slate-200">请选择元素</p>
                <p className="mt-1 text-xs text-slate-400 dark:text-slate-500">
                  点击画布中的节点或连线进行属性配置
                </p>
              </div>
            ) : (
              <div className="space-y-5">
                <div className="rounded-lg bg-slate-50 px-3 py-2 text-xs dark:bg-slate-900/60">
                  <p className="text-slate-500 dark:text-slate-400">
                    元素 ID：<span className="font-mono">{panel.id}</span>
                  </p>
                  <p className="mt-0.5 text-slate-500 dark:text-slate-400">
                    类型：{TYPE_LABELS[panel.type] || panel.type}
                  </p>
                </div>

                <div>
                  <label className="form-label">名称</label>
                  <input
                    className="input"
                    value={panel.name}
                    onChange={(e) => handleNameChange(e.target.value)}
                  />
                </div>

                {panel.isUserTask && (
                  <div>
                    <label className="form-label">候选角色（写入 flowable:candidateGroups）</label>
                    <div className="space-y-1.5">
                      {roles.length === 0 && (
                        <p className="text-xs text-slate-400 dark:text-slate-500">
                          角色列表加载失败或为空
                        </p>
                      )}
                      {roles.map((role) => (
                        <label
                          key={role.id}
                          className="flex cursor-pointer items-center gap-2.5 rounded-lg border border-slate-200 px-3 py-2 text-sm transition-colors hover:border-primary-300 dark:border-slate-600 dark:hover:border-primary-500/50"
                        >
                          <input
                            type="checkbox"
                            className="h-4 w-4 rounded border-slate-300 text-primary-600 focus:ring-primary-500 dark:border-slate-500 dark:bg-slate-900"
                            checked={panel.groups.includes(role.code)}
                            onChange={() => toggleGroup(role.code)}
                          />
                          <span className="text-slate-700 dark:text-slate-200">{role.name}</span>
                          <code className="ml-auto rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-500 dark:bg-slate-900 dark:text-slate-400">
                            {role.code}
                          </code>
                        </label>
                      ))}
                    </div>
                  </div>
                )}

                {panel.isSequenceFlow && (
                  <div>
                    <label className="form-label">条件表达式（conditionExpression）</label>
                    <textarea
                      className="input min-h-[72px] resize-y font-mono text-xs"
                      value={panel.condition}
                      placeholder="如 ${amount > 10000}"
                      onChange={(e) => handleConditionChange(e.target.value)}
                    />
                    <p className="mt-1 text-xs text-slate-400 dark:text-slate-500">
                      网关出线可按表单字段设置分支条件，如{' '}
                      <code className="rounded bg-slate-100 px-1 dark:bg-slate-900">
                        {'${amount > 10000}'}
                      </code>
                      ；留空表示无条件
                    </p>
                  </div>
                )}

                {!panel.isUserTask && !panel.isSequenceFlow && (
                  <p className="rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-400 dark:bg-slate-900/60 dark:text-slate-500">
                    该元素类型暂无额外配置项
                  </p>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
