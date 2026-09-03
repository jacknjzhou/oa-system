import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import type { FormField, InstanceDetail } from '../types'
import { cancelInstance, getInstance } from '../api/process'
import { getTemplate } from '../api/template'
import { getStoredUser } from '../api/auth'
import { useToast } from '../components/Toast'
import Modal from '../components/Modal'
import BpmnViewer from '../components/BpmnViewer'
import ApprovalTimeline from '../components/ApprovalTimeline'
import InstanceInfoCard from '../components/InstanceInfoCard'
import EmptyState, { ErrorState, LoadingState } from '../components/EmptyState'
import { parseFormConfig } from '../utils/format'

export default function ProcessTracking() {
  const { id } = useParams<{ id: string }>()
  const { showToast } = useToast()

  const [detail, setDetail] = useState<InstanceDetail | null>(null)
  const [formFields, setFormFields] = useState<FormField[] | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [confirmCancel, setConfirmCancel] = useState(false)
  const [cancelling, setCancelling] = useState(false)

  const user = getStoredUser()

  const loadDetail = useCallback(async () => {
    if (!id) return
    setLoading(true)
    setLoadError('')
    try {
      const instanceDetail = await getInstance(id)
      setDetail(instanceDetail)
      try {
        const template = await getTemplate(instanceDetail.instance.defId)
        setFormFields(parseFormConfig(template.formConfig).fields)
      } catch {
        setFormFields(null)
      }
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : '流程实例加载失败')
    } finally {
      setLoading(false)
    }
  }, [id])

  useEffect(() => {
    loadDetail()
  }, [loadDetail])

  const handleCancel = async () => {
    if (!detail) return
    setCancelling(true)
    try {
      await cancelInstance(detail.instance.id)
      showToast('流程已取消', 'success')
      setConfirmCancel(false)
      await loadDetail()
    } catch (err) {
      showToast(err instanceof Error ? err.message : '取消失败', 'error')
    } finally {
      setCancelling(false)
    }
  }

  if (loading) {
    return <LoadingState text="正在加载流程详情…" />
  }

  if (loadError || !detail) {
    return (
      <div className="mx-auto max-w-5xl p-6">
        <div className="card">
          <ErrorState message={loadError || '流程实例不存在'} />
        </div>
      </div>
    )
  }

  const { instance, bpmnXml, completedActivityIds, currentActivityIds, approvalRecords } = detail
  const isInitiator = !!user && instance.initiatorId === user.id
  const cancellable = instance.status === 'RUNNING' && isInitiator

  return (
    <div className="mx-auto max-w-5xl space-y-5 p-6">
      {/* 实例信息卡 */}
      <InstanceInfoCard instance={instance} formFields={formFields} />

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

      {/* 发起人取消流程 */}
      {cancellable && (
        <div className="flex justify-end">
          <button type="button" className="btn btn-danger" onClick={() => setConfirmCancel(true)}>
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z"
              />
            </svg>
            取消流程
          </button>
        </div>
      )}

      {/* 取消确认弹窗 */}
      <Modal
        open={confirmCancel}
        title="取消流程确认"
        onClose={() => setConfirmCancel(false)}
        footer={
          <>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => setConfirmCancel(false)}
              disabled={cancelling}
            >
              再想想
            </button>
            <button type="button" className="btn btn-danger" onClick={handleCancel} disabled={cancelling}>
              {cancelling ? '取消中…' : '确认取消流程'}
            </button>
          </>
        }
      >
        <p className="text-sm leading-6 text-slate-600 dark:text-slate-300">
          确定要取消「{instance.title}」吗？取消后流程将终止，无法恢复。
        </p>
      </Modal>
    </div>
  )
}
