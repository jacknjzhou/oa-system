import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import type { DocumentDTO } from '../types'
import { archiveDocument, getDocuments } from '../api/document'
import DocumentFormModal from '../components/DocumentFormModal'
import { DocStatusBadge, DOC_TYPE_LABELS, SecrecyBadge, UrgencyBadge } from '../components/DocumentBadges'
import Modal from '../components/Modal'
import EmptyState, { ErrorState, LoadingState } from '../components/EmptyState'
import { useToast } from '../components/Toast'
import { formatDateTime } from '../utils/format'

export default function DocumentList() {
  const navigate = useNavigate()
  const { showToast } = useToast()
  const [documents, setDocuments] = useState<DocumentDTO[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState('')
  const [keyword, setKeyword] = useState('')
  const [formOpen, setFormOpen] = useState(false)
  const [editingDoc, setEditingDoc] = useState<DocumentDTO | undefined>()
  const [confirmArchive, setConfirmArchive] = useState<DocumentDTO | null>(null)
  const [acting, setActing] = useState(false)

  const load = useCallback(async (title?: string) => {
    setLoading(true)
    setLoadError('')
    try {
      setDocuments(await getDocuments(title?.trim() || undefined))
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : '公文列表加载失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    load()
  }, [load])

  const handleSearch = (e: FormEvent) => {
    e.preventDefault()
    load(keyword)
  }

  const handleArchive = async () => {
    if (!confirmArchive) return
    setActing(true)
    try {
      await archiveDocument(confirmArchive.id)
      showToast('公文已归档', 'success')
      setConfirmArchive(null)
      load(keyword)
    } catch (err) {
      showToast(err instanceof Error ? err.message : '归档失败', 'error')
    } finally {
      setActing(false)
    }
  }

  return (
    <div className="mx-auto max-w-6xl p-6">
      <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-xl font-bold text-slate-900 dark:text-slate-100">公文</h2>
          <p className="mt-0.5 text-sm text-slate-500 dark:text-slate-400">
            起草、查阅与归档公司公文
          </p>
        </div>
        <button type="button" className="btn btn-primary" onClick={() => setFormOpen(true)}>
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
          </svg>
          新建公文
        </button>
      </div>

      <form onSubmit={handleSearch} className="mb-4 flex max-w-sm gap-2">
        <input
          className="input"
          placeholder="按标题搜索…"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
        />
        <button type="submit" className="btn shrink-0">搜索</button>
      </form>

      {loading ? (
        <LoadingState text="正在加载公文列表…" />
      ) : loadError ? (
        <div className="card">
          <ErrorState message={loadError} onRetry={() => load(keyword)} />
        </div>
      ) : documents.length === 0 ? (
        <div className="card">
          <EmptyState
            icon="📄"
            title="暂无公文"
            description="点击右上角「新建公文」起草第一份公文"
          />
        </div>
      ) : (
        <div className="card overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-200 bg-slate-50 text-left text-xs uppercase tracking-wide text-slate-500 dark:border-slate-700 dark:bg-slate-900/60 dark:text-slate-400">
                  <th className="px-5 py-3 font-medium">文号</th>
                  <th className="px-5 py-3 font-medium">标题</th>
                  <th className="px-5 py-3 font-medium">类型</th>
                  <th className="px-5 py-3 font-medium">缓急</th>
                  <th className="px-5 py-3 font-medium">密级</th>
                  <th className="px-5 py-3 font-medium">起草人</th>
                  <th className="px-5 py-3 font-medium">状态</th>
                  <th className="px-5 py-3 font-medium">创建时间</th>
                  <th className="px-5 py-3 text-right font-medium">操作</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 dark:divide-slate-700">
                {documents.map((doc) => (
                  <tr
                    key={doc.id}
                    className="cursor-pointer transition-colors hover:bg-slate-50 dark:hover:bg-slate-700/40"
                    onClick={() => navigate(`/documents/${doc.id}`)}
                  >
                    <td className="px-5 py-3.5">
                      <code className="rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-600 dark:bg-slate-900 dark:text-slate-300">
                        {doc.docNo}
                      </code>
                    </td>
                    <td className="px-5 py-3.5 font-medium text-slate-900 dark:text-slate-100">{doc.title}</td>
                    <td className="px-5 py-3.5 text-slate-600 dark:text-slate-300">
                      {DOC_TYPE_LABELS[doc.docType] ?? doc.docType}
                    </td>
                    <td className="px-5 py-3.5"><UrgencyBadge urgency={doc.urgency} /></td>
                    <td className="px-5 py-3.5"><SecrecyBadge level={doc.secrecyLevel} /></td>
                    <td className="px-5 py-3.5 text-slate-600 dark:text-slate-300">{doc.authorName}</td>
                    <td className="px-5 py-3.5"><DocStatusBadge status={doc.status} /></td>
                    <td className="px-5 py-3.5 text-slate-500 dark:text-slate-400">{formatDateTime(doc.createdAt)}</td>
                    <td className="px-5 py-3.5">
                      <div className="flex justify-end gap-1.5" onClick={(e) => e.stopPropagation()}>
                        <button
                          type="button"
                          className="rounded-lg px-2.5 py-1 text-xs font-medium text-primary-600 transition-colors hover:bg-primary-50 dark:text-primary-400 dark:hover:bg-primary-500/10"
                          onClick={() => navigate(`/documents/${doc.id}`)}
                        >
                          查看
                        </button>
                        {doc.status === 'DRAFT' && (
                          <button
                            type="button"
                            className="rounded-lg px-2.5 py-1 text-xs font-medium text-blue-600 transition-colors hover:bg-blue-50 dark:text-blue-400 dark:hover:bg-blue-500/10"
                            onClick={() => {
                              setEditingDoc(doc)
                              setFormOpen(true)
                            }}
                          >
                            编辑
                          </button>
                        )}
                        {doc.status !== 'ARCHIVED' && (
                          <button
                            type="button"
                            className="rounded-lg px-2.5 py-1 text-xs font-medium text-red-600 transition-colors hover:bg-red-50 dark:text-red-400 dark:hover:bg-red-500/10"
                            onClick={() => setConfirmArchive(doc)}
                          >
                            归档
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

      <DocumentFormModal
        open={formOpen}
        document={editingDoc}
        onClose={() => {
          setFormOpen(false)
          setEditingDoc(undefined)
        }}
        onSaved={() => load(keyword)}
      />


      <Modal
        open={confirmArchive !== null}
        title="归档公文"
        onClose={() => setConfirmArchive(null)}
        footer={
          <>
            <button type="button" className="btn" onClick={() => setConfirmArchive(null)} disabled={acting}>
              取消
            </button>
            <button type="button" className="btn btn-danger" onClick={handleArchive} disabled={acting}>
              {acting ? '归档中…' : '确认归档'}
            </button>
          </>
        }
      >
        <p className="text-sm text-slate-600 dark:text-slate-300">
          归档后公文将不再可编辑，确定归档「{confirmArchive?.title}」？
        </p>
      </Modal>
    </div>
  )
}
