import { useState } from 'react'
import type { FieldPerm, FlowApproverType, FlowNodeSpec, FlowSignMode, FormField, Role, UserSummary } from '../types'

/**
 * 可视化流程设计器（P2-2）：三栏式
 * 左：添加审批节点 / 右：节点属性（审批人 4 类 × 签署 3 模式）/ 中：节点链
 * 对照致碟云「可视化流程设计」：审批人 角色/用户/主管/发起人 + 单签/会签/并签。
 */

interface FlowDesignerProps {
  spec: FlowNodeSpec[]
  onChange: (nodes: FlowNodeSpec[]) => void
  roles: Role[]
  users: UserSummary[]
  /** 表单字段（表单操作权限配置用） */
  fields?: FormField[]
  /** 发起人节点的字段权限 */
  initiatorPerms?: Record<string, FieldPerm>
  onInitiatorPermsChange?: (perms: Record<string, FieldPerm>) => void
}

const APPROVER_LABELS: Record<FlowApproverType, string> = {
  role: '指定角色',
  user: '指定用户',
  supervisor: '发起人主管',
  initiator: '发起人自己',
}

const SIGN_LABELS: Record<FlowSignMode, string> = {
  single: '单签（一人通过）',
  countersign: '会签（须全部通过）',
  cosign: '并签（任一人通过）',
}

let seq = 0
function newId(): string {
  seq += 1
  return `node_${Date.now().toString(36)}_${seq}`
}

export default function FlowDesigner({ spec, onChange, roles, users, fields, initiatorPerms, onInitiatorPermsChange }: FlowDesignerProps) {
  const [permTab, setPermTab] = useState<'node' | 'initiator'>('node')

  const fieldPermList = (context: 'node' | 'initiator') => (
    <div className="space-y-1.5">
      {(fields ?? []).length === 0 && <p className="text-xs text-slate-400">模板尚未配置表单字段（先在「表单配置」添加）</p>}
      {(fields ?? []).map((f) => {
        const isInitiator = context === 'initiator'
        const current = isInitiator
          ? (initiatorPerms?.[f.key] ?? 'editable')
          : (selected?.fieldPerms?.[f.key] ?? 'readonly')
        const handle = (perm: FieldPerm) => {
          if (isInitiator) {
            onInitiatorPermsChange?.({ ...(initiatorPerms ?? {}), [f.key]: perm })
          } else if (selected) {
            update(selected.id, { fieldPerms: { ...(selected.fieldPerms ?? {}), [f.key]: perm } })
          }
        }
        return (
          <div key={f.key} className="flex items-center gap-2">
            <span className="min-w-0 flex-1 truncate text-sm text-slate-700 dark:text-slate-200">
              {f.label || f.key}
            </span>
            <select
              className="w-24 rounded border border-slate-300 bg-white px-1.5 py-1 text-xs dark:border-slate-600 dark:bg-slate-800"
              value={current}
              onChange={(e) => handle(e.target.value as FieldPerm)}
            >
              <option value="editable">可编辑</option>
              <option value="readonly">只读</option>
              <option value="hidden">隐藏</option>
            </select>
          </div>
        )
      })}
    </div>
  )
  const [selectedId, setSelectedId] = useState<string | null>(spec[0]?.id ?? null)
  const selected = spec.find((n) => n.id === selectedId) ?? null

  const update = (id: string, patch: Partial<FlowNodeSpec>) => {
    onChange(spec.map((n) => (n.id === id ? { ...n, ...patch } : n)))
  }

  const addNode = () => {
    const node: FlowNodeSpec = {
      id: newId(),
      name: `审批${spec.length + 1}`,
      approverType: 'role',
      roles: [],
      signMode: 'single',
    }
    onChange([...spec, node])
    setSelectedId(node.id)
  }

  const removeNode = (id: string) => {
    onChange(spec.filter((n) => n.id !== id))
    if (selectedId === id) setSelectedId(null)
  }

  const move = (id: string, dir: -1 | 1) => {
    const idx = spec.findIndex((n) => n.id === id)
    const to = idx + dir
    if (idx < 0 || to < 0 || to >= spec.length) return
    const next = [...spec]
    ;[next[idx], next[to]] = [next[to], next[idx]]
    onChange(next)
  }

  // 主管审批固定单签；用户/发起人自己 固定单签
  const signDisabled = !!(selected && selected.approverType !== 'role')

  return (
    <div className="grid h-full grid-cols-[220px_1fr_300px] gap-0">
      {/* 左：控件库 */}
      <aside className="flex flex-col gap-3 overflow-y-auto border-r border-slate-200 p-4 dark:border-slate-700">
        <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-400">节点库</h3>
        <button
          type="button"
          className="flex items-center gap-2 rounded-lg border border-dashed border-slate-300 px-3 py-2.5 text-sm text-slate-600 transition-colors hover:border-primary-400 hover:text-primary-600 dark:border-slate-600 dark:text-slate-300 dark:hover:border-primary-500"
          onClick={addNode}
        >
          <span className="flex h-7 w-7 items-center justify-center rounded-full bg-primary-50 text-primary-600 dark:bg-primary-500/15 dark:text-primary-400">
            +
          </span>
          添加审批节点
        </button>
        <div className="mt-2 rounded-lg bg-slate-50 p-3 text-xs leading-relaxed text-slate-500 dark:bg-slate-900/60 dark:text-slate-400">
          <p>· 节点自上而下依次审批</p>
          <p className="mt-1">· 审批人：角色 / 用户 / 发起人主管 / 发起人</p>
          <p className="mt-1">· 签署：单签 / 会签 / 并签（仅角色可选）</p>
          <p className="mt-1">· 每个节点可逐字段设置 可编辑/只读/隐藏</p>
        </div>
        <button
          type="button"
          onClick={() => setPermTab('initiator')}
          className={`rounded-lg border px-3 py-2 text-left text-xs transition-colors ${
            permTab === 'initiator'
              ? 'border-primary-400 bg-primary-50 text-primary-700 dark:bg-primary-500/15 dark:text-primary-400'
              : 'border-slate-200 text-slate-500 hover:border-slate-300 dark:border-slate-600 dark:text-slate-400'
          }`}
        >
          发起人字段权限（默认全部可编辑）
        </button>
        {permTab === 'initiator' && <div className="mt-2">{fieldPermList('initiator')}</div>}
      </aside>

      {/* 中：节点链 */}
      <section className="flex flex-col items-center overflow-y-auto p-6">
        <div className="flex h-12 w-32 items-center justify-center rounded-full border-2 border-emerald-400 bg-emerald-50 text-sm font-medium text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-400">
          开始（发起）
        </div>
        {spec.length === 0 && (
          <p className="my-4 rounded-lg border border-dashed border-amber-300 bg-amber-50 px-4 py-2 text-xs text-amber-700 dark:border-amber-500/50 dark:bg-amber-500/10 dark:text-amber-400">
            尚未添加审批节点——模板可保存发布，但无法发起流程
          </p>
        )}
        {spec.map((node, i) => (
          <div key={node.id} className="flex flex-col items-center">
            {i > 0 && <div className="h-6 w-0.5 bg-slate-300 dark:bg-slate-600" />}
            <button
              type="button"
              onClick={() => setSelectedId(node.id)}
              className={`w-56 rounded-lg border px-3 py-2.5 text-left shadow-sm transition-colors ${
                selectedId === node.id
                  ? 'border-primary-500 bg-primary-50 dark:bg-primary-500/15'
                  : 'border-slate-300 bg-white hover:border-primary-300 dark:border-slate-600 dark:bg-slate-800'
              }`}
            >
              <div className="flex items-center justify-between">
                <span className="text-sm font-medium text-slate-800 dark:text-slate-100">{node.name}</span>
                <span className="flex gap-0.5">
                  <span
                    role="button"
                    tabIndex={0}
                    className="rounded px-1.5 text-xs text-slate-400 hover:bg-slate-100 hover:text-slate-600 dark:hover:bg-slate-700"
                    title="上移"
                    onClick={(e) => {
                      e.stopPropagation()
                      move(node.id, -1)
                    }}
                    onKeyDown={(e) => e.key === 'Enter' && move(node.id, -1)}
                  >
                    ↑
                  </span>
                  <span
                    role="button"
                    tabIndex={0}
                    className="rounded px-1.5 text-xs text-slate-400 hover:bg-slate-100 hover:text-slate-600 dark:hover:bg-slate-700"
                    title="下移"
                    onClick={(e) => {
                      e.stopPropagation()
                      move(node.id, 1)
                    }}
                    onKeyDown={(e) => e.key === 'Enter' && move(node.id, 1)}
                  >
                    ↓
                  </span>
                  <span
                    role="button"
                    tabIndex={0}
                    className="rounded px-1.5 text-xs text-red-400 hover:bg-red-50 hover:text-red-600 dark:hover:bg-red-500/15"
                    title="删除"
                    onClick={(e) => {
                      e.stopPropagation()
                      removeNode(node.id)
                    }}
                    onKeyDown={(e) => e.key === 'Enter' && removeNode(node.id)}
                  >
                    ✕
                  </span>
                </span>
              </div>
              <div className="mt-1 flex flex-wrap gap-1 text-xs text-slate-500 dark:text-slate-400">
                <span className="rounded bg-slate-100 px-1.5 py-0.5 dark:bg-slate-700">
                  {APPROVER_LABELS[node.approverType]}
                  {node.approverType === 'role' && node.roles.length > 0 && `：${node.roles.join('/')}`}
                  {node.approverType === 'user' && node.username ? `：${node.username}` : ''}
                </span>
                {node.signMode !== 'single' && (
                  <span className="rounded bg-violet-100 px-1.5 py-0.5 text-violet-600 dark:bg-violet-500/15 dark:text-violet-400">
                    {SIGN_LABELS[node.signMode].split('（')[0]}
                  </span>
                )}
              </div>
            </button>
          </div>
        ))}
        {spec.length > 0 && (
          <>
            <div className="h-6 w-0.5 bg-slate-300 dark:bg-slate-600" />
            <div className="flex h-12 w-32 items-center justify-center rounded-full border-2 border-red-400 bg-red-50 text-sm font-medium text-red-600 dark:bg-red-500/10 dark:text-red-400">
              结束
            </div>
          </>
        )}
      </section>

      {/* 右：属性面板 */}
      <aside className="flex flex-col gap-4 overflow-y-auto border-l border-slate-200 p-4 dark:border-slate-700">
        <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-400">节点属性</h3>
        {!selected ? (
          <p className="text-sm text-slate-400">选择中间节点进行配置</p>
        ) : (
          <>
            <div>
              <label className="form-label">节点名称</label>
              <input
                className="input"
                value={selected.name}
                onChange={(e) => update(selected.id, { name: e.target.value })}
              />
            </div>

            <div>
              <label className="form-label">审批人类型</label>
              <select
                className="input"
                value={selected.approverType}
                onChange={(e) =>
                  update(selected.id, {
                    approverType: e.target.value as FlowApproverType,
                    signMode: 'single',
                  })
                }
              >
                {(Object.keys(APPROVER_LABELS) as FlowApproverType[]).map((t) => (
                  <option key={t} value={t}>
                    {APPROVER_LABELS[t]}
                  </option>
                ))}
              </select>
            </div>

            {selected.approverType === 'role' && (
              <div>
                <label className="form-label">审批角色（会签/并签可选多个）</label>
                <div className="space-y-1.5">
                  {roles.length === 0 && <p className="text-xs text-slate-400">暂无可用角色</p>}
                  {roles.map((r) => (
                    <label
                      key={r.id}
                      className="flex cursor-pointer items-center gap-2 rounded-lg border border-slate-200 px-3 py-1.5 text-sm dark:border-slate-600"
                    >
                      <input
                        type="checkbox"
                        className="h-4 w-4 rounded border-slate-300 text-primary-600"
                        checked={selected.roles.includes(r.code)}
                        onChange={() =>
                          update(selected.id, {
                            roles: selected.roles.includes(r.code)
                              ? selected.roles.filter((c) => c !== r.code)
                              : [...selected.roles, r.code],
                          })
                        }
                      />
                      <span className="text-slate-700 dark:text-slate-200">{r.name}</span>
                      <code className="ml-auto text-xs text-slate-400">{r.code}</code>
                    </label>
                  ))}
                </div>
              </div>
            )}

            {selected.approverType === 'user' && (
              <div>
                <label className="form-label">审批人</label>
                <select
                  className="input"
                  value={selected.userId ?? ''}
                  onChange={(e) => {
                    const u = users.find((x) => String(x.id) === e.target.value)
                    update(selected.id, { userId: u ? Number(u.id) : null, username: u?.username ?? null })
                  }}
                >
                  <option value="">请选择</option>
                  {users.map((u) => (
                    <option key={u.id} value={u.id}>
                      {u.realName}（{u.username}）
                    </option>
                  ))}
                </select>
              </div>
            )}

            {selected.approverType === 'supervisor' && (
              <p className="rounded-lg bg-slate-50 p-3 text-xs leading-relaxed text-slate-500 dark:bg-slate-900/60 dark:text-slate-400">
                审批人 = 发起人的直属主管（在员工管理中维护），无主管时回退给发起人本人。
              </p>
            )}

            <div>
              <label className="form-label">表单操作权限（未设置默认全部只读）</label>
              {fieldPermList('node')}
            </div>

            {selected.approverType === 'role' && (
              <div>
                <label className="form-label">签署模式</label>
                <select
                  className="input"
                  value={selected.signMode}
                  disabled={signDisabled}
                  onChange={(e) => update(selected.id, { signMode: e.target.value as FlowSignMode })}
                >
                  {(Object.keys(SIGN_LABELS) as FlowSignMode[]).map((m) => (
                    <option key={m} value={m}>
                      {SIGN_LABELS[m]}
                    </option>
                  ))}
                </select>
                {selected.signMode !== 'single' && selected.roles.length === 0 && (
                  <p className="mt-1.5 text-xs text-amber-600 dark:text-amber-400">
                    会签/并签请至少选择一个角色
                  </p>
                )}
              </div>
            )}
          </>
        )}
      </aside>
    </div>
  )
}
