import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import type { Template } from '../types'
import { disableTemplate, getTemplates, publishTemplate } from '../api/template'
import StatusBadge from '../components/Badge'
import DynamicForm from '../components/DynamicForm'
import type { Role } from '../types'
import { getRoles } from '../api/user'
import { parseFormConfig } from '../utils/format'
import {
  getTemplatePermissions,
  saveTemplatePermissions,
  type ApprovalPermissionRow,
} from '../api/template'
import Modal from '../components/Modal'
import EmptyState, { ErrorState, LoadingState } from '../components/EmptyState'
import { useToast } from '../components/Toast'
import { categoryEmoji, formatDateTime } from '../utils/format'

interface ConfirmAction {
  type: 'publish' | 'disable'
  template: Template
}

export default function TemplateList() {
  const navigate = useNavigate()
  const { showToast } = useToast()
  const [templates, setTemplates] = useState<Template[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [confirm, setConfirm] = useState<ConfirmAction | null>(null)
  const [acting, setActing] = useState(false)
  const [previewTarget, setPreviewTarget] = useState<Template | null>(null)
  const [permTarget, setPermTarget] = useState<Template | null>(null)

  const load = useCallback(() => {
    setLoading(true)
    setLoadError('')
    getTemplates()
      .then(setTemplates)
      .catch((err) => {
        const message = err instanceof Error ? err.message : '模板列表加载失败'
        setLoadError(message)
      })
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const refreshRow = async (id: string) => {
    const updated = await getTemplates()
    setTemplates((prev) => prev.map((t) => (t.id === id ? updated.find((u) => u.id === id) ?? t : t)))
  }

  const openPermissionModal = async (template: Template) => {
    setPermTarget(template)
  }

  const handleConfirmAction = async () => {
    if (!confirm) return
    setActing(true)
    try {
      const request =
        confirm.type === 'publish' ? publishTemplate(confirm.template.id) : disableTemplate(confirm.template.id)
      await request
      showToast(confirm.type === 'publish' ? '模板已发布' : '模板已停用', 'success')
      setConfirm(null)
      load()
    } catch (err) {
      showToast(err instanceof Error ? err.message : '操作失败', 'error')
    } finally {
      setActing(false)
    }
  }

  return (
    <div className="mx-auto max-w-6xl p-6">
      <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-xl font-bold text-slate-900 dark:text-slate-100">审批模板</h2>
          <p className="mt-0.5 text-sm text-slate-500 dark:text-slate-400">
            管理审批流程模板：设计 BPMN 流程、配置表单并发布
          </p>
        </div>
        <Link to="/templates/new" className="btn btn-primary">
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
          </svg>
          新建模板
        </Link>
      </div>

      {loading ? (
        <LoadingState text="正在加载模板列表…" />
      ) : loadError ? (
        <div className="card">
          <ErrorState message={loadError} />
        </div>
      ) : templates.length === 0 ? (
        <div className="card">
          <EmptyState
            icon="🧩"
            title="暂无审批模板"
            description="点击右上角「新建模板」创建第一个审批流程"
          />
        </div>
      ) : (
        <div className="card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500 dark:border-slate-700 dark:bg-slate-900/60 dark:text-slate-400">
                  <th className="px-5 py-3 font-medium">模板名称</th>
                  <th className="px-5 py-3 font-medium">Key</th>
                  <th className="px-5 py-3 font-medium">版本</th>
                  <th className="px-5 py-3 font-medium">分类</th>
                  <th className="px-5 py-3 font-medium">状态</th>
                  <th className="px-5 py-3 font-medium">发布时间</th>
                  <th className="px-5 py-3 text-right font-medium">操作</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-700">
                {templates.map((template) => (
                  <tr
                    key={template.id}
                    className="transition-colors hover:bg-slate-50 dark:hover:bg-slate-700/40"
                  >
                    <td className="px-5 py-3.5">
                      <div className="flex items-center gap-2.5">
                        <span className="text-lg">{categoryEmoji(template.category)}</span>
                        <span className="font-medium text-slate-900 dark:text-slate-100">{template.name}</span>
                      </div>
                    </td>
                    <td className="px-5 py-3.5">
                      <code className="rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-600 dark:bg-slate-900 dark:text-slate-300">
                        {template.defKey}
                      </code>
                    </td>
                    <td className="px-5 py-3.5 text-slate-600 dark:text-slate-300">v{template.version}</td>
                    <td className="px-5 py-3.5 text-slate-600 dark:text-slate-300">
                      {template.category || '-'}
                    </td>
                    <td className="px-5 py-3.5">
                      <div className="flex items-center gap-1.5">
                        <StatusBadge status={template.status} />
                        {template.flowReady === false && (
                          <span className="rounded bg-amber-100 px-1.5 py-0.5 text-xs text-amber-700 dark:bg-amber-500/15 dark:text-amber-400">
                            流程未设计
                          </span>
                        )}
                      </div>
                    </td>
                    <td className="px-5 py-3.5 text-slate-500 dark:text-slate-400">
                      {formatDateTime(template.publishedAt)}
                    </td>
                    <td className="px-5 py-3.5">
                      <div className="flex justify-end gap-1.5">
                        <button
                          type="button"
                          className="rounded-lg px-2.5 py-1 text-xs font-medium text-slate-500 transition-colors hover:bg-slate-100 dark:text-slate-400 dark:hover:bg-slate-800"
                          onClick={() => setPreviewTarget(template)}
                        >
                          预览
                        </button>
                        <button
                          type="button"
                          className="rounded-lg px-2.5 py-1 text-xs font-medium text-violet-600 transition-colors hover:bg-violet-50 dark:text-violet-400 dark:hover:bg-violet-500/10"
                          onClick={() => openPermissionModal(template)}
                        >
                          权限
                        </button>
                        <button
                          type="button"
                          className="rounded-lg px-2.5 py-1 text-xs font-medium text-primary-600 transition-colors hover:bg-primary-50 dark:text-primary-400 dark:hover:bg-primary-500/10"
                          onClick={() => navigate(`/templates/${template.id}`)}
                        >
                          编辑
                        </button>
                        {template.status !== 'PUBLISHED' && (
                          <button
                            type="button"
                            className="rounded-lg px-2.5 py-1 text-xs font-medium text-emerald-600 transition-colors hover:bg-emerald-50 dark:text-emerald-400 dark:hover:bg-emerald-500/10"
                            onClick={() => setConfirm({ type: 'publish', template })}
                          >
                            发布
                          </button>
                        )}
                        {template.status === 'PUBLISHED' && (
                          <button
                            type="button"
                            className="rounded-lg px-2.5 py-1 text-xs font-medium text-red-600 transition-colors hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-500/10"
                            onClick={() => setConfirm({ type: 'disable', template })}
                          >
                            停用
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <PreviewModal template={previewTarget} onClose={() => setPreviewTarget(null)} />
      <PermissionModal
        template={permTarget}
        onClose={() => setPermTarget(null)}
        onSaved={(t) => {
          showToast('审批权限已保存', 'success')
          setPermTarget(null)
          void refreshRow(t.id)
        }}
      />

      {/* 发布 / 停用确认弹窗 */}
      <Modal
        open={confirm !== null}
        title={confirm?.type === 'publish' ? '发布模板' : '停用模板'}
        onClose={() => setConfirm(null)}
        footer={
          <>
            <button type="button" className="btn btn-secondary" onClick={() => setConfirm(null)} disabled={acting}>
              取消
            </button>
            <button
              type="button"
              className={`btn ${confirm?.type === 'publish' ? 'btn-success' : 'btn-danger'}`}
              onClick={handleConfirmAction}
              disabled={acting}
            >
              {acting ? '处理中…' : '确认'}
            </button>
          </>
        }
      >
        <p className="text-sm leading-6 text-slate-600 dark:text-slate-300">
          {confirm?.type === 'publish' ? (
            <>
              确定发布模板「{confirm?.template.name}」吗？发布后员工可在「发起审批」中发起该流程。
            </>
          ) : (
            <>
              确定停用模板「{confirm?.template.name}」吗？停用后将无法发起新的申请，进行中的流程不受影响。
            </>
          )}
        </p>
      </Modal>
    </div>
  )
}

/** 审批预览（AS-06）：按模板表单渲染只读样例，必填字段带 * */
function PreviewModal({ template, onClose }: { template: Template | null; onClose: () => void }) {
  if (!template) return null
  const { fields } = parseFormConfig(template.formConfig)
  return (
    <Modal open title={`审批预览 —— ${template.name}`} onClose={onClose} footer={
      <button type="button" className="btn btn-secondary" onClick={onClose}>关闭</button>
    }>
      <p className="mb-3 text-xs text-slate-400">以下为表单只读预览；<span className="text-red-500">*</span> 为必填字段。</p>
      <DynamicForm
        fields={fields}
        values={{}}
        onChange={() => {}}
        readOnly
      />
    </Modal>
  )
}

const PERM_ITEMS: Array<{ key: 'start' | 'view' | 'manage' | 'edit'; label: string }> = [
  { key: 'start', label: '申请' },
  { key: 'view', label: '查看' },
  { key: 'manage', label: '管理' },
  { key: 'edit', label: '编辑' },
]

/** 审批功能权限（AS-07）：角色 × 权限项 */
function PermissionModal({
  template,
  onClose,
  onSaved,
}: {
  template: Template | null
  onClose: () => void
  onSaved: (t: Template) => void
}) {
  const { showToast } = useToast()
  const [roles, setRoles] = useState<Role[]>([])
  const [rows, setRows] = useState<Record<string, Set<string>>>({})
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!template) return
    setRows({})
    Promise.all([
      getRoles().catch(() => [] as Role[]),
      getTemplatePermissions(template.id).catch(() => [] as ApprovalPermissionRow[]),
    ]).then(([roleList, permList]) => {
      setRoles(roleList)
      const next: Record<string, Set<string>> = {}
      for (const role of roleList) next[role.code] = new Set()
      for (const row of permList) {
        if (!next[row.roleCode]) next[row.roleCode] = new Set()
        next[row.roleCode].add(row.perm)
      }
      setRows(next)
    })
  }, [template])

  if (!template) return null

  const toggle = (roleCode: string, perm: string) => {
    setRows((prev) => {
      const set = new Set(prev[roleCode] ?? [])
      if (set.has(perm)) set.delete(perm)
      else set.add(perm)
      return { ...prev, [roleCode]: set }
    })
  }

  const handleSave = async () => {
    setSaving(true)
    try {
      const payload = Object.entries(rows)
        .filter(([, perms]) => perms.size > 0)
        .map(([roleCode, perms]) => ({ roleCode, perms: Array.from(perms) }))
      await saveTemplatePermissions(template.id, payload)
      onSaved(template)
    } catch (err) {
      showToast(err instanceof Error ? err.message : '权限保存失败', 'error')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal open title={`审批权限 —— ${template.name}`} onClose={onClose} footer={
      <>
        <button type="button" className="btn btn-secondary" onClick={onClose} disabled={saving}>取消</button>
        <button type="button" className="btn btn-primary" onClick={handleSave} disabled={saving}>
          {saving ? '保存中…' : '保存权限'}
        </button>
      </>
    }>
      <p className="mb-3 text-xs text-slate-400">
        按角色配置该审批的功能权限：申请（发起）/ 查看 / 管理（催办、撤回等）/ 编辑（流程与表单维护）。
      </p>
      <div className="overflow-x-auto">
        <table className="min-w-full text-sm">
          <thead>
            <tr className="border-b border-slate-100 text-left text-xs text-slate-400 dark:border-slate-700">
              <th className="py-2 pr-4 font-medium">角色</th>
              {PERM_ITEMS.map((item) => (
                <th key={item.key} className="px-2 py-2 text-center font-medium">{item.label}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {roles.map((role) => (
              <tr key={role.code} className="border-b border-slate-50 dark:border-slate-800">
                <td className="py-2.5 pr-4">
                  <span className="font-medium text-slate-700 dark:text-slate-200">{role.name}</span>
                  <span className="ml-1.5 text-xs text-slate-400">{role.code}</span>
                </td>
                {PERM_ITEMS.map((item) => (
                  <td key={item.key} className="px-2 py-2.5 text-center">
                    <input
                      type="checkbox"
                      className="h-4 w-4 accent-blue-600"
                      checked={rows[role.code]?.has(item.key) ?? false}
                      onChange={() => toggle(role.code, item.key)}
                    />
                  </td>
                ))}
              </tr>
            ))}
            {roles.length === 0 && (
              <tr>
                <td colSpan={5} className="py-4 text-center text-slate-400">暂无角色</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </Modal>
  )
}
