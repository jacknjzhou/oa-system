import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import type { DocumentDTO } from '../types'
import { archiveDocument, getDocument } from '../api/document'
import DocumentFormModal from '../components/DocumentFormModal'
import {
  DOC_STATUS_LABELS,
  DOC_TYPE_LABELS,
  DocStatusBadge,
  SecrecyBadge,
  UrgencyBadge,
} from '../components/DocumentBadges'
import Modal from '../components/Modal'
import { ErrorState, LoadingState } from '../components/EmptyState'
import { useToast } from '../components/Toast'
import { formatDateTime } from '../utils/format'

function MetaItem({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div>
      <dt className="text-xs text-slate-500 dark:text-slate-400">{label}</dt>
      <dd className="mt-0.5 text-sm text-slate-900 dark:text-slate-100">{value}</dd>
    </div>
  )
}

export default function DocumentDetail() {
  const { id } = useParams()
  const docId = Number(id)
  const { showToast } = useToast()
  const [doc, setDoc] = useState<DocumentDTO | null>(null)
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [editOpen, setEditOpen] = useState(false)
  const [confirmArchive, setConfirmArchive] = useState(false)
  const [acting, setActing] = useState(false)

  const load = useCallback(() => {
    setLoading(true)
    setLoadError('')
    getDocument(docId)
      .then(setDoc)
      .catch((err) => setLoadError(err instanceof Error ? err.message : '公文加载失败'))
      .finally(() => setLoading(false))
  }, [docId])

  useEffect(() => {
    load()
  }, [load])

  const handleArchive = async () => {
    if (!doc) return
    setActing(true)
    try {
      await archiveDocument(doc.id)
      showToast('公文已归档', 'success')
      setConfirmArchive(false)
      load()
    } catch (err) {
      showToast(err instanceof Error ? err.message : '归档失败', 'error')
    } finally {
      setActing(false)
    }
  }

  if (loading) return <LoadingState text="正在加载公文…" />
  if (loadError) {
    return (
      <div className="mx-auto max-w-4xl p-6">
        <div className="card">
          <ErrorState message={loadError} onRetry={load} />
        </div>
      </div>
    )
  }
  if (!doc) return null

  return (
    <div className="mx-auto max-w-4xl p-6">
      <div className="mb-4 flex items-center gap-2 text-sm">
        <Link to="/documents" className="text-primary-600 hover:underline dark:text-primary-400">
          公文
        </Link>
        <span className="text-slate-400">/</span>
        <span className="text-slate-600 dark:text-slate-300">{doc.title}</span>
      </div>

      <div className="card p-6">
        <div className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-200 pb-5 dark:border-slate-700">
          <div>
            <div className="flex items-center gap-2.5">
              <h2 className="text-lg font-bold text-slate-900 dark:text-slate-100">{doc.title}</h2>
              <DocStatusBadge status={doc.status} />
            </div>
            <code className="mt-1 inline-block rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-500 dark:bg-slate-900 dark:text-slate-400">
              {doc.docNo}
            </code>
          </div>
          <div className="flex gap-2">
            {doc.status === 'DRAFT' && (
              <button type="button" className="btn btn-secondary" onClick={() => setEditOpen(true)}>
                编辑
              </button>
            )}
            {doc.status !== 'ARCHIVED' && (
              <button type="button" className="btn btn-danger" onClick={() => setConfirmArchive(true)}>
                归档
              </button>
            )}
          </div>
        </div>

        <dl className="mt-5 grid grid-cols-2 gap-x-6 gap-y-4 sm:grid-cols-3">
          <MetaItem
            label="类型"
            value={
              <span className="inline-flex items-center gap-1.5">
                {DOC_TYPE_LABELS[doc.docType] ?? doc.docType}
                <UrgencyBadge urgency={doc.urgency} />
                <SecrecyBadge level={doc.secrecyLevel} />
              </span>
            }
          />
          <MetaItem label="起草人" value={doc.authorName} />
          <MetaItem label="创建时间" value={formatDateTime(doc.createdAt)} />
          {doc.publishedAt && <MetaItem label="发布时间" value={formatDateTime(doc.publishedAt)} />}
          {doc.archivedAt && <MetaItem label="归档时间" value={formatDateTime(doc.archivedAt)} />}
          <MetaItem label="最近更新" value={formatDateTime(doc.updatedAt)} />
        </dl>

        <div className="mt-6 border-t border-slate-200 pt-5 dark:border-slate-700">
          <h3 className="mb-3 text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
            正文
          </h3>
          <div className="whitespace-pre-wrap text-sm leading-7 text-slate-800 dark:text-slate-200">
            {doc.content || <span className="italic text-slate-400">（无正文）</span>}
          </div>
        </div>
      </div>

      <DocumentFormModal
        open={editOpen}
        document={doc}
        onClose={() => setEditOpen(false)}
        onSaved={() => load()}
      />

      <Modal
        open={confirmArchive}
        title="归档公文"
        onClose={() => setConfirmArchive(false)}
        footer={
          <>
            <button type="button" className="btn btn-secondary" onClick={() => setConfirmArchive(false)} disabled={acting}>
              取消
            </button>
            <button type="button" className="btn btn-danger" onClick={handleArchive} disabled={acting}>
              {acting ? '归档中…' : '确认归档'}
            </button>
          </>
        }
      >
        <p className="text-sm text-slate-600 dark:text-slate-300">
          归档后公文将不再可编辑，确定归档「{doc.title}」（{DOC_STATUS_LABELS[doc.status]}）？
        </p>
      </Modal>
    </div>
  )
}
