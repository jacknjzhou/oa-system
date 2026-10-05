import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { orgApi, type OrgNode } from '../api/org'
import { userApi, type UserRow } from '../api/user'
import { getStoredUser } from '../api/auth'
import { useToast } from '../components/Toast'
import EmptyState, { ErrorState, LoadingState } from '../components/EmptyState'

interface OrgFormState {
  orgName: string
  orgCode: string
  parentId: number | null
  sortOrder: number | null
}

function flatten(list: OrgNode[]): OrgNode[] {
  const out: OrgNode[] = []
  const walk = (n: OrgNode) => {
    out.push(n)
    n.children.forEach(walk)
  }
  list.forEach(walk)
  return out
}

/** 递归部门树（选中高亮 + 直属在职人数徽标）。 */
function TreeNode({
  node,
  selected,
  onSelect,
  onEdit,
  depth,
  canManage,
}: {
  node: OrgNode
  selected: number | null
  onSelect: (id: number | null) => void
  onEdit: (n: OrgNode) => void
  depth: number
  canManage: boolean
}) {
  return (
    <div>
      <div
        role="button"
        onClick={() => onSelect(node.id === selected ? null : node.id)}
        className={`group flex w-full cursor-pointer items-center gap-2 rounded-lg px-3 py-2 text-sm transition-colors ${
          node.id === selected ? 'bg-primary-50 font-medium text-primary-700' : 'text-slate-700 hover:bg-slate-50'
        }`}
        style={{ paddingLeft: `${12 + depth * 16}px` }}
      >
        <span className="flex-1 truncate">{node.orgName}</span>
        <span className="text-xs text-slate-400">{node.memberCount}</span>
        {canManage && (
          <button
            onClick={(e) => {
              e.stopPropagation()
              onEdit(node)
            }}
            className="hidden text-xs text-slate-400 hover:text-primary-600 group-hover:block"
          >
            编辑
          </button>
        )}
      </div>
      {node.children.map((c) => (
        <TreeNode
          key={c.id}
          node={c}
          selected={selected}
          onSelect={onSelect}
          onEdit={onEdit}
          depth={depth + 1}
          canManage={canManage}
        />
      ))}
    </div>
  )
}

/**
 * 部门管理（SY-02）：左侧部门树（人数徽标）+ CRUD；右侧选中部门的在职员工 + 「创建新员工」。
 * 写操作需 hr:dept（无权限隐藏按钮）；删除/改父受后端保护（子部门/在职员工存在时 400）。
 */
export default function Departments() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const { showToast } = useToast()
  const user = getStoredUser()
  const canManage =
    (user?.roles ?? []).includes('ADMIN') || (user?.permissions ?? []).includes('hr:dept')

  const [tree, setTree] = useState<OrgNode[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(
    searchParams.get('org') ? Number(searchParams.get('org')) : null,
  )
  const [members, setMembers] = useState<UserRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [editing, setEditing] = useState<OrgNode | 'new' | null>(null)
  const [form, setForm] = useState<OrgFormState>({ orgName: '', orgCode: '', parentId: null, sortOrder: null })
  const [saving, setSaving] = useState(false)

  const refresh = useCallback(async () => {
    try {
      setError('')
      setTree(await orgApi.tree())
    } catch {
      setError('部门加载失败')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  useEffect(() => {
    if (!selectedId) {
      setMembers([])
      return
    }
    userApi
      .list({ status: 'active', orgId: selectedId })
      .then(setMembers)
      .catch(() => setMembers([]))
  }, [selectedId, tree])

  const openNew = (parentId: number | null = null) => {
    setEditing('new')
    setForm({ orgName: '', orgCode: '', parentId, sortOrder: null })
  }

  const openEdit = (n: OrgNode) => {
    setEditing(n)
    setForm({ orgName: n.orgName, orgCode: n.orgCode || '', parentId: n.parentId, sortOrder: n.sortOrder })
  }

  const save = async () => {
    if (!form.orgName.trim()) {
      showToast('部门名称必填', 'error')
      return
    }
    setSaving(true)
    try {
      const body = {
        orgName: form.orgName.trim(),
        orgCode: form.orgCode.trim() || null,
        parentId: form.parentId,
        sortOrder: form.sortOrder,
      }
      if (editing === 'new') await orgApi.create(body)
      else if (editing) await orgApi.update(editing.id, body)
      showToast('已保存')
      setEditing(null)
      await refresh()
    } catch (e) {
      showToast(e instanceof Error ? e.message : '保存失败', 'error')
    } finally {
      setSaving(false)
    }
  }

  const remove = async (n: OrgNode) => {
    if (!window.confirm(`确定删除部门「${n.orgName}」？（存在子部门或在职员工时将被拒绝）`)) return
    try {
      await orgApi.remove(n.id)
      if (selectedId === n.id) setSelectedId(null)
      showToast('已删除')
      await refresh()
    } catch (e) {
      showToast(e instanceof Error ? e.message : '删除失败', 'error')
    }
  }

  const selected = selectedId ? flatten(tree).find((n) => n.id === selectedId) ?? null : null

  return (
    <div className="grid gap-6 lg:grid-cols-[280px_1fr]">
      <div>
        <div className="rounded-xl border border-slate-200 bg-white p-3 dark:border-slate-700 dark:bg-slate-800">
          <div className="mb-2 flex items-center justify-between px-1">
            <span className="text-sm font-medium text-slate-600">部门结构</span>
            {canManage && (
              <button onClick={() => openNew(null)} className="text-xs text-primary-600 hover:underline">
                + 新建部门
              </button>
            )}
          </div>
          <button
            onClick={() => setSelectedId(null)}
            className={`mb-1 w-full rounded-lg px-3 py-2 text-left text-sm ${
              selectedId === null ? 'bg-primary-50 font-medium text-primary-700' : 'text-slate-500 hover:bg-slate-50'
            }`}
          >
            全部员工
          </button>
          {tree.map((n) => (
            <TreeNode
              key={n.id}
              node={n}
              selected={selectedId}
              onSelect={setSelectedId}
              onEdit={openEdit}
              depth={0}
              canManage={canManage}
            />
          ))}
          {canManage && selected && (
            <div className="mt-2 flex gap-2 border-t border-slate-100 px-3 py-2 text-xs dark:border-slate-700">
              <button onClick={() => openNew(selected.id)} className="text-primary-600 hover:underline">
                新建子部门
              </button>
              <button onClick={() => openEdit(selected)} className="text-slate-500 hover:underline">
                编辑
              </button>
              <button onClick={() => void remove(selected)} className="text-red-500 hover:underline">
                删除
              </button>
            </div>
          )}
        </div>
      </div>

      <div className="rounded-xl border border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-800">
        <div className="flex items-center justify-between border-b border-slate-100 px-5 py-4 dark:border-slate-700">
          <div>
            <h3 className="text-sm font-semibold text-slate-800 dark:text-slate-100">
              {selected ? `${selected.orgName} · 在职员工` : '全部在职员工'}
            </h3>
            <p className="text-xs text-slate-400">{members.length} 人</p>
          </div>
          {canManage && (
            <button
              onClick={() =>
                navigate(`/system/users?create=1&org=${selected ? selected.id : ''}`)
              }
              className="rounded-lg bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700"
            >
              创建新员工
            </button>
          )}
        </div>
        {loading ? (
          <LoadingState />
        ) : error ? (
          <ErrorState onRetry={refresh} message={error} />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-slate-100 text-left text-xs text-slate-400 dark:border-slate-700">
                  <th className="px-5 py-3">姓名</th>
                  <th className="px-3 py-3">账号</th>
                  <th className="px-3 py-3">部门</th>
                  <th className="px-3 py-3">岗位</th>
                  <th className="px-3 py-3">手机</th>
                  <th className="px-5 py-3">角色</th>
                </tr>
              </thead>
              <tbody>
                {members.map((m) => (
                  <tr key={m.id} className="border-b border-slate-50 hover:bg-slate-50/50 dark:border-slate-700/50">
                    <td className="px-5 py-3 font-medium text-slate-700 dark:text-slate-200">{m.realName}</td>
                    <td className="px-3 py-3 text-slate-500">{m.username}</td>
                    <td className="px-3 py-3 text-slate-500">{m.orgName ?? '—'}</td>
                    <td className="px-3 py-3 text-slate-500">{m.position ?? '—'}</td>
                    <td className="px-3 py-3 text-slate-500">{m.phone ?? '—'}</td>
                    <td className="px-5 py-3 text-slate-500">{m.roles?.join('、') || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {members.length === 0 && <EmptyState title="该部门暂无在职员工" />}
          </div>
        )}
      </div>

      {editing && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 p-4">
          <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl dark:bg-slate-800">
            <h3 className="mb-4 text-base font-semibold text-slate-800 dark:text-slate-100">
              {editing === 'new' ? '新建部门' : `编辑部门：${editing.orgName}`}
            </h3>
            <div className="space-y-3">
              <div>
                <label className="mb-1 block text-xs text-slate-500">部门名称 *</label>
                <input
                  value={form.orgName}
                  onChange={(e) => setForm({ ...form, orgName: e.target.value })}
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-700"
                  autoFocus
                />
              </div>
              <div>
                <label className="mb-1 block text-xs text-slate-500">
                  编码（可选，缺省自动生成；不可改——引用保护）
                </label>
                <input
                  value={form.orgCode}
                  disabled={editing !== 'new'}
                  onChange={(e) => setForm({ ...form, orgCode: e.target.value })}
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm disabled:opacity-50 dark:border-slate-600 dark:bg-slate-700"
                />
              </div>
              <div>
                <label className="mb-1 block text-xs text-slate-500">上级部门</label>
                <select
                  value={form.parentId ?? ''}
                  onChange={(e) => setForm({ ...form, parentId: e.target.value ? Number(e.target.value) : null })}
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-700"
                >
                  <option value="">（顶级）</option>
                  {flatten(tree)
                    .filter((n) => !(editing !== 'new' && editing && (n.id === editing.id || n.id === editing.parentId)))
                    .map((n) => (
                      <option key={n.id} value={n.id}>
                        {n.orgName}
                      </option>
                    ))}
                </select>
              </div>
              <div>
                <label className="mb-1 block text-xs text-slate-500">排序</label>
                <input
                  type="number"
                  value={form.sortOrder ?? ''}
                  onChange={(e) => setForm({ ...form, sortOrder: e.target.value === '' ? null : Number(e.target.value) })}
                  className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm dark:border-slate-600 dark:bg-slate-700"
                />
              </div>
            </div>
            <div className="mt-5 flex justify-end gap-2">
              <button
                onClick={() => setEditing(null)}
                className="rounded-lg border border-slate-300 px-4 py-2 text-sm text-slate-600 dark:border-slate-600 dark:text-slate-300"
              >
                取消
              </button>
              <button
                onClick={() => void save()}
                disabled={saving}
                className="rounded-lg bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 disabled:opacity-50"
              >
                {saving ? '保存中…' : '保存'}
              </button>
            </div>
           
          </div>
        </div>
      )}
    </div>
  )
}
