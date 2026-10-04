import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import type { FormField, TaskDetail, UserSummary } from '../types'
import { getTask, completeTask, rejectTask, denyTask, transferTask } from '../api/task'
import { getTemplate } from '../api/template'
import { getUsers } from '../api/user'
import { getStoredUser } from '../api/auth'
import { useToast } from '../components/Toast'
import Modal from '../components/Modal'
import BpmnViewer from '../components/BpmnViewer'
import ApprovalTimeline from '../components/ApprovalTimeline'
import InstanceInfoCard from '../components/InstanceInfoCard'
import EmptyState, { ErrorState, LoadingState } from '../components/EmptyState'
import { formatDateTime, parseFormConfig } from '../utils/format'
import { parseUserTasks } from '../utils/bpmn'

type ActionModal = 'approve' | 'reject' | 'deny' | 'transfer' | null

export default function ApprovalForm() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { showToast } = useToast()

  const [detail, setDetail] = useState<TaskDetail | null>(null)
  const [formFields, setFormFields] = useState<FormField[] | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')

  const [modal, setModal] = useState<ActionModal>(null)
  const [comment, setComment] = useState('')
  const [toNodeKey, setToNodeKey] = useState('')
  const [toUserId, setToUserId] = useState('')
  const [users, setUsers] = useState<UserSummary[]>([])
  const [submitting, setSubmitting] = useState(false)

  const user = getStoredUser()

  const loadDetail = useCallback(async () => {
    if (!id) return
    setLoading(true)
    setLoadError('')
    try {
      const taskDetail = await getTask(id)
      setDetail(taskDetail)
      // 拉取模板以按 formConfig 展示业务数据（失败则回退为原始键展示）
      try {
        const template = await getTemplate(taskDetail.instance.defId)
        setFormFields(parseFormConfig(template.formConfig).fields)
      } catch {
        setFormFields(null)
      }
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : '任务加载失败')
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => {
    loadDetail()
  }, [loadDetail])

  const openModal = (action: Exclude<ActionModal, null>) => {
    setComment('')
    setToNodeKey('')
    setToUserId('')
    setModal(action)
    if (action === 'transfer') {
      getUsers()
        .then(setUsers)
        .catch((err) => {
          showToast(err instanceof Error ? err.message : '用户列表加载失败', 'error')
        })
    }
  }

  const closeModal = () => {
    if (submitting) return
    setModal(null)
  }

  const handleSubmitApprove = async () => {
    if (!detail) return
    setSubmitting(true)
    try {
      await completeTask(detail.task.id, { comment })
      showToast('审批通过成功', 'success')
      navigate('/tasks/todo')
    } catch (err) {
      showToast(err instanceof Error ? err.message : '操作失败', 'error')
    } finally {
      setSubmitting(false)
    }
  }

  const handleSubmitReject = async () => {
    if (!detail) return
    if (!comment.trim()) {
      showToast('请填写驳回意见', 'error')
      return
    }
    setSubmitting(true)
    try {
      await rejectTask(detail.task.id, {
        comment: comment.trim(),
        toNodeKey: toNodeKey || undefined,
      })
      showToast(toNodeKey ? '已驳回到指定节点' : '已驳回', 'success')
      navigate('/tasks/todo')
    } catch (err) {
      showToast(err instanceof Error ? err.message : '操作失败', 'error')
    } finally {
      setSubmitting(false)
    }
  }

  const handleSubmitDeny = async () => {
    if (!detail) return
    if (!comment.trim()) {
      showToast('请填写拒绝意见', 'error')
      return
    }
    setSubmitting(true)
    try {
      await denyTask(detail.task.id, { comment: comment.trim() })
      showToast('已拒绝，流程终止', 'success')
      navigate('/tasks/todo')
    } catch (err) {
      showToast(err instanceof Error ? err.message : '操作失败', 'error')
    } finally {
      setSubmitting(false)
    }
  }

  const handleSubmitTransfer = async () => {
    if (!detail) return
    if (!toUserId) {
      showToast('请选择转办人', 'error')
      return
    }
    if (!comment.trim()) {
      showToast('请填写转办意见', 'error')
      return
    }
    setSubmitting(true)
    try {
      await transferTask(detail.task.id, { toUserId, comment: comment.trim() })
      showToast('转办成功', 'success')
      navigate('/tasks/todo')
    } catch (err) {
      showToast(err instanceof Error ? err.message : '操作失败', 'error')
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return <LoadingState text="正在加载任务详情…" />
  }

  if (loadError || !detail) {
    return (
      <div className="mx-auto max-w-5xl p-6">
        <div className="card">
          <ErrorState message={loadError || '任务不存在'} />
        </div>
      </div>
    )
  }

  const { task, instance, bpmnXml, completedActivityIds, currentActivityIds, approvalRecords } = detail

  // 当前登录人是否可操作：任务待办且（被指派给自己 或 候选角色命中）
  const canOperate =
    task.status === 'PENDING' &&
    !!user &&
    (task.assignee === user.id ||
      task.assignee === user.username ||
      task.candidateRoles.some((role) => user.roles.includes(role)))

  // 可驳回节点：BPMN XML 中的 userTask（排除当前节点）
  const rejectableNodes = parseUserTasks(bpmnXml).filter((node) => node.id !== task.nodeKey)

  return (
    <div className="mx-auto max-w-5xl space-y-5 p-6">
      {/* 实例信息卡 */}
      <InstanceInfoCard instance={instance} formFields={formFields} />

      {/* 任务信息 */}
      <div className="card p-5">
        <div className="flex flex-wrap items-center gap-x-6 gap-y-2 text-sm">
          <span className="text-slate-500 dark:text-slate-400">
            任务节点：
            <span className="font-medium text-slate-800 dark:text-slate-200">{task.nodeName}</span>
          </span>
          <span className="text-slate-500 dark:text-slate-400">
            创建时间：
            <span className="font-medium text-slate-800 dark:text-slate-200">
              {formatDateTime(task.createTime)}
            </span>
          </span>
          {task.candidateRoles.length > 0 && (
            <span className="text-slate-500 dark:text-slate-400">
              候选角色：
              <span className="font-medium text-slate-800 dark:text-slate-200">
                {task.candidateRoles.join('、')}
              </span>
            </span>
          )}
        </div>
      </div>

      {/* BPMN 流程图 */}
      <div className="card p-5">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
          <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">流程图</h3>
          <div className="flex items-center gap-4 text-xs text-slate-500 dark:text-slate-400">
            <span className="flex items-center gap-1.5">
              <span className="h-2.5 w-2.5 rounded-sm bg-emerald-500" />已完成
            </span>
            <span className="flex items-center gap-1.5">
              <span className="h-2.5 w-2.5 rounded-sm bg-blue-500" />进行中
            </span>
          </div>
        </div>
        {bpmnXml ? (
          <BpmnViewer
            xml={bpmnXml}
            completedActivityIds={completedActivityIds}
            currentActivityIds={currentActivityIds}
          />
        ) : (
          <EmptyState icon="🗺️" title="暂无流程图" />
        )}
      </div>

      {/* 审批历史 */}
      <div className="card p-5">
        <h3 className="mb-4 text-sm font-semibold text-slate-900 dark:text-slate-100">审批历史</h3>
        <ApprovalTimeline records={approvalRecords} />
      </div>

      {/* 底部操作栏 */}
      {canOperate ? (
        <div className="card sticky bottom-4 flex flex-wrap items-center justify-between gap-3 border-primary-200 p-4 dark:border-primary-500/30">
          <p className="text-sm text-slate-500 dark:text-slate-400">
            该任务等待你处理，请谨慎操作
          </p>
          <div className="flex flex-wrap gap-2">
            <button type="button" className="btn btn-success" onClick={() => openModal('approve')}>
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
              </svg>
              通过
            </button>
            <button type="button" className="btn btn-danger" onClick={() => openModal('reject')}>
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
              驳回（回节点）
            </button>
            <button type="button" className="btn btn-danger" onClick={() => openModal('deny')}>
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M18.364 18.364A9 9 0 105.636 5.636M12 3v9l.01 6M9.9 15h4.2" />
              </svg>
              拒绝（终止）
            </button>
            <button type="button" className="btn btn-violet" onClick={() => openModal('transfer')}>
              <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4"
                />
              </svg>
              转办
            </button>
          </div>
        </div>
      ) : (
        <div className="card p-4 text-center text-sm text-slate-500 dark:text-slate-400">
          {task.status === 'PENDING' ? '该任务当前不由你处理，仅供查看' : '任务已办结，仅供查看'}
          {task.comment && (
            <span className="ml-2 text-slate-600 dark:text-slate-300">处理意见：{task.comment}</span>
          )}
        </div>
      )}

      {/* 通过弹窗 */}
      <Modal
        open={modal === 'approve'}
        title="通过审批"
        onClose={closeModal}
        footer={
          <>
            <button type="button" className="btn btn-secondary" onClick={closeModal} disabled={submitting}>
              取消
            </button>
            <button type="button" className="btn btn-success" onClick={handleSubmitApprove} disabled={submitting}>
              {submitting ? '提交中…' : '确认通过'}
            </button>
          </>
        }
      >
        <div>
          <label className="form-label" htmlFor="approve-comment">
            审批意见（可选）
          </label>
          <textarea
            id="approve-comment"
            className="input min-h-[80px] resize-y"
            placeholder="请输入审批意见"
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            disabled={submitting}
          />
        </div>
      </Modal>

      {/* 驳回弹窗 */}
      <Modal
        open={modal === 'reject'}
        title="驳回审批"
        onClose={closeModal}
        footer={
          <>
            <button type="button" className="btn btn-secondary" onClick={closeModal} disabled={submitting}>
              取消
            </button>
            <button type="button" className="btn btn-danger" onClick={handleSubmitReject} disabled={submitting}>
              {submitting ? '提交中…' : '确认驳回'}
            </button>
          </>
        }
      >
        <div className="space-y-4">
          <div>
            <label className="form-label" htmlFor="reject-target">
              驳回目标
            </label>
            <select
              id="reject-target"
              className="input"
              value={toNodeKey}
              onChange={(e) => setToNodeKey(e.target.value)}
              disabled={submitting}
            >
              <option value="">整单驳回（流程结束）</option>
              {rejectableNodes.map((node) => (
                <option key={node.id} value={node.id}>
                  驳回到：{node.name}（{node.id}）
                </option>
              ))}
            </select>
            <p className="mt-1 text-xs text-slate-400 dark:text-slate-500">
              不选择节点时，默认整单驳回并结束流程
            </p>
          </div>
          <div>
            <label className="form-label" htmlFor="reject-comment">
              驳回意见 <span className="text-red-500">*</span>
            </label>
            <textarea
              id="reject-comment"
              className="input min-h-[80px] resize-y"
              placeholder="请输入驳回原因"
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              disabled={submitting}
            />
          </div>
        </div>
      </Modal>

      {/* 拒绝弹窗（终止流程） */}
      <Modal
        open={modal === 'deny'}
        title="拒绝审批"
        onClose={closeModal}
        footer={
          <>
            <button type="button" className="btn btn-secondary" onClick={closeModal} disabled={submitting}>
              取消
            </button>
            <button type="button" className="btn btn-danger" onClick={handleSubmitDeny} disabled={submitting}>
              {submitting ? '提交中…' : '确认拒绝'}
            </button>
          </>
        }
      >
        <div>
          <label className="form-label" htmlFor="deny-comment">
            拒绝意见 <span className="text-red-500">*</span>
          </label>
          <textarea
            id="deny-comment"
            className="input min-h-[80px] resize-y"
            placeholder="请输入拒绝原因（流程将终止）"
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            disabled={submitting}
          />
          <p className="mt-1 text-xs text-red-400">拒绝后流程立即结束，发起人可修改后重新发起</p>
        </div>
      </Modal>

      {/* 转办弹窗 */}
      <Modal
        open={modal === 'transfer'}
        title="转办任务"
        onClose={closeModal}
        footer={
          <>
            <button type="button" className="btn btn-secondary" onClick={closeModal} disabled={submitting}>
              取消
            </button>
            <button type="button" className="btn btn-violet" onClick={handleSubmitTransfer} disabled={submitting}>
              {submitting ? '提交中…' : '确认转办'}
            </button>
          </>
        }
      >
        <div className="space-y-4">
          <div>
            <label className="form-label" htmlFor="transfer-user">
              转办给 <span className="text-red-500">*</span>
            </label>
            <select
              id="transfer-user"
              className="input"
              value={toUserId}
              onChange={(e) => setToUserId(e.target.value)}
              disabled={submitting}
            >
              <option value="">请选择转办人</option>
              {users.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.realName || u.username}
                  {u.position ? `（${u.position}）` : ''}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="form-label" htmlFor="transfer-comment">
              转办意见 <span className="text-red-500">*</span>
            </label>
            <textarea
              id="transfer-comment"
              className="input min-h-[80px] resize-y"
              placeholder="请说明转办原因"
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              disabled={submitting}
            />
          </div>
        </div>
      </Modal>
    </div>
  )
}
