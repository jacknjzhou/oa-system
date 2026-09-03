import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import type { Template } from '../types'
import { disableTemplate, getTemplates, publishTemplate } from '../api/template'
import StatusBadge from '../components/Badge'
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
                      <StatusBadge status={template.status} />
                    </td>
                    <td className="px-5 py-3.5 text-slate-500 dark:text-slate-400">
                      {formatDateTime(template.publishedAt)}
                    </td>
                    <td className="px-5 py-3.5">
                      <div className="flex justify-end gap-1.5">
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
