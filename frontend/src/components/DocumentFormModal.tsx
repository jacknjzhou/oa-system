import { useEffect, useState, type FormEvent } from 'react'
import Modal from './Modal'
import { useToast } from './Toast'
import { createDocument, updateDocument, type DocumentPayload } from '../api/document'
import { DOC_TYPE_LABELS, SECRECY_LABELS, URGENCY_LABELS } from './DocumentBadges'
import type { DocType, DocUrgency, DocumentDTO, SecrecyLevel } from '../types'

interface DocumentFormModalProps {
  open: boolean
  /** 传入则为编辑，不传为新建 */
  document?: DocumentDTO
  onClose: () => void
  onSaved: (doc: DocumentDTO) => void
}

const emptyForm: DocumentPayload = {
  title: '',
  content: '',
  docType: 'NOTICE',
  urgency: 'NORMAL',
  secrecyLevel: 'INTERNAL',
}

export default function DocumentFormModal({ open, document, onClose, onSaved }: DocumentFormModalProps) {
  const { showToast } = useToast()
  const [form, setForm] = useState<DocumentPayload>(emptyForm)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (open) {
      setForm(
        document
          ? {
              title: document.title,
              content: document.content ?? '',
              docType: document.docType ?? 'NOTICE',
              urgency: document.urgency ?? 'NORMAL',
              secrecyLevel: document.secrecyLevel ?? 'INTERNAL',
            }
          : emptyForm
      )
    }
  }, [open, document])

  const set = <K extends keyof DocumentPayload>(key: K, value: DocumentPayload[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }))

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (!form.title.trim()) {
      showToast('标题不能为空', 'error')
      return
    }
    setSaving(true)
    try {
      const saved = document
        ? await updateDocument(document.id, form)
        : await createDocument(form)
      showToast(document ? '公文已更新' : '公文已创建', 'success')
      onSaved(saved)
      onClose()
    } catch (err) {
      showToast(err instanceof Error ? err.message : '保存失败', 'error')
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      open={open}
      title={document ? '编辑公文' : '新建公文'}
      onClose={onClose}
      widthClass="max-w-2xl"
      footer={
        <>
          <button type="button" className="btn" onClick={onClose} disabled={saving}>
            取消
          </button>
          <button type="submit" form="document-form" className="btn btn-primary" disabled={saving}>
            {saving ? '保存中…' : '保存'}
          </button>
        </>
      }
    >
      <form id="document-form" className="space-y-4" onSubmit={handleSubmit}>
        <div>
          <label className="form-label" htmlFor="doc-title">标题 *</label>
          <input
            id="doc-title"
            className="input"
            value={form.title}
            onChange={(e) => set('title', e.target.value)}
            placeholder="请输入公文标题"
            maxLength={200}
            autoFocus
          />
        </div>

        <div className="grid grid-cols-3 gap-3">
          <div>
            <label className="form-label" htmlFor="doc-type">类型</label>
            <select
              id="doc-type"
              className="input"
              value={form.docType}
              onChange={(e) => set('docType', e.target.value as DocType)}
            >
              {Object.entries(DOC_TYPE_LABELS).map(([value, label]) => (
                <option key={value} value={value}>{label}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="form-label" htmlFor="doc-urgency">缓急</label>
            <select
              id="doc-urgency"
              className="input"
              value={form.urgency}
              onChange={(e) => set('urgency', e.target.value as DocUrgency)}
            >
              {Object.entries(URGENCY_LABELS).map(([value, label]) => (
                <option key={value} value={value}>{label}</option>
              ))}
            </select>
          </div>
          <div>
            <label className="form-label" htmlFor="doc-secrecy">密级</label>
            <select
              id="doc-secrecy"
              className="input"
              value={form.secrecyLevel}
              onChange={(e) => set('secrecyLevel', e.target.value as SecrecyLevel)}
            >
              {Object.entries(SECRECY_LABELS).map(([value, label]) => (
                <option key={value} value={value}>{label}</option>
              ))}
            </select>
          </div>
        </div>

        <div>
          <label className="form-label" htmlFor="doc-content">正文</label>
          <textarea
            id="doc-content"
            className="input min-h-[160px] resize-y"
            value={form.content}
            onChange={(e) => set('content', e.target.value)}
            placeholder="请输入公文正文…"
            rows={8}
          />
        </div>
      </form>
    </Modal>
  )
}
