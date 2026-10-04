import { useCallback, useEffect, useMemo, useState } from 'react'
import apiClient from '../api/client'
import { getUsers } from '../api/user'
import type { UserSummary } from '../types'
import { useToast } from '../components/Toast'

interface GroupRow {
  id: number
  code: string
  name: string
  enabled: boolean
  permissionIds: number[]
  members: { id: number; username: string; realName?: string | null }[]
}

interface PermRow {
  id: number
  code: string
  name: string
  groupName: string
}

/** 权组与权限（SY-03） */
export default function PermissionGroups() {
  const { showToast } = useToast()
  const [groups, setGroups] = useState<GroupRow[]>([])
  const [perms, setPerms] = useState<PermRow[]>([])
  const [users, setUsers] = useState<UserSummary[]>([])
  const [selected, setSelected] = useState<GroupRow | null>(null)
  const [permDraft, setPermDraft] = useState<number[]>([])
  const [memberDraft, setMemberDraft] = useState<number[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [newGroup, setNewGroup] = useState({ code: '', name: '' })

  const load = useCallback(async () => {
    try {
      setError('')
      const [g, p, u] = await Promise.all([
        apiClient.get<GroupRow[]>('/permission-groups').then((r) => r.data),
        apiClient.get<PermRow[]>('/permission-groups/permissions').then((r) => r.data),
        getUsers(),
      ])
      setGroups(g)
      setPerms(p)
      setUsers(u)
      if (!selected) {
        const first = g[0]
        setSelected(first ?? null)
        if (first) {
          setPermDraft(first.permissionIds)
          setMemberDraft(first.members.map((m) => m.id))
        }
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载失败')
    }
  }, [selected])

  useEffect(() => {
    load()
  }, [load])

  const permGroups = useMemo(() => {
    const map = new Map<string, PermRow[]>()
    for (const p of perms) {
      const arr = map.get(p.groupName) ?? []
      arr.push(p)
      map.set(p.groupName, arr)
    }
    return Array.from(map.entries())
  }, [perms])

  const pick = (g: GroupRow) => {
    setSelected(g)
    setPermDraft(g.permissionIds)
    setMemberDraft(g.members.map((m) => m.id))
  }

  const save = async (kind: 'permissions' | 'members') => {
    if (!selected) return
    setBusy(true)
    try {
      const body = { ids: kind === 'permissions' ? permDraft : memberDraft }
      await apiClient.put(`/permission-groups/${selected.id}/${kind}`, body)
      showToast('已保存', 'success')
      await load()
    } catch (err) {
      showToast(err instanceof Error ? err.message : '保存失败', 'error')
    } finally {
      setBusy(false)
    }
  }

  const create = async () => {
    if (!newGroup.code || !newGroup.name) {
      showToast('请填写编码与名称', 'error')
      return
    }
    setBusy(true)
    try {
      await apiClient.post('/permission-groups', newGroup)
      setNewGroup({ code: '', name: '' })
      showToast('已创建', 'success')
      await load()
    } catch (err) {
      showToast(err instanceof Error ? err.message : '创建失败', 'error')
    } finally {
      setBusy(false)
    }
  }

  const togglePerm = (id: number) =>
    setPermDraft((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]))

  const toggleMember = (id: number) =>
    setMemberDraft((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]))

  return (
    <div className="flex flex-col gap-6">
      <div className="grid gap-4 lg:grid-cols-[280px_1fr]">
        {/* 权组列表 */}
        <div className="rounded-2xl bg-white p-4 shadow-sm dark:bg-slate-800">
          <h3 className="mb-3 text-sm font-semibold text-slate-700 dark:text-slate-200">权组</h3>
          <div className="flex flex-col gap-1.5">
            {groups.map((g) => (
              <button
                key={g.id}
                onClick={() => pick(g)}
                className={`rounded-lg px-3 py-2 text-left text-sm transition ${
                  selected?.id === g.id
                    ? 'bg-indigo-50 font-medium text-indigo-700 dark:bg-indigo-500/20 dark:text-indigo-300'
                    : 'text-slate-600 hover:bg-slate-50 dark:text-slate-300 dark:hover:bg-slate-700/50'
                }`}
              >
                {g.name}
                <span className="ml-1.5 text-xs text-slate-400">
                  {g.code} · {g.members.length}人 · {g.permissionIds.length}权限
                </span>
                {!g.enabled && <span className="ml-1 text-xs text-rose-500">已停用</span>}
              </button>
            ))}
          </div>
          <div className="mt-4 flex flex-col gap-2 border-t border-slate-100 pt-3 dark:border-slate-700">
            <input
              value={newGroup.code}
              onChange={(e) => setNewGroup({ ...newGroup, code: e.target.value.toUpperCase() })}
              placeholder="编码（如 HR_GROUP）"
              className="rounded-lg border border-slate-200 px-2.5 py-1.5 text-xs dark:border-slate-600 dark:bg-slate-700"
            />
            <input
              value={newGroup.name}
              onChange={(e) => setNewGroup({ ...newGroup, name: e.target.value })}
              placeholder="名称"
              className="rounded-lg border border-slate-200 px-2.5 py-1.5 text-xs dark:border-slate-600 dark:bg-slate-700"
            />
            <button
              disabled={busy}
              onClick={create}
              className="rounded-lg bg-indigo-500 py-1.5 text-xs font-medium text-white hover:bg-indigo-600 disabled:opacity-50"
            >
              新增权组
            </button>
          </div>
        </div>

        {/* 编辑区 */}
        <div className="rounded-2xl bg-white p-5 shadow-sm dark:bg-slate-800">
          {!selected ? (
            <p className="text-sm text-slate-400">请选择左侧权组</p>
          ) : (
            <>
              <div className="mb-4 flex items-center gap-3">
                <h3 className="text-base font-semibold text-slate-800 dark:text-slate-100">
                  {selected.name}
                </h3>
                <span className="text-xs text-slate-400">{selected.code}</span>
              </div>
              <div className="grid gap-5 md:grid-cols-2">
                <div>
                  <p className="mb-2 text-xs font-medium text-slate-500 dark:text-slate-400">
                    权限清单（{permDraft.length} 项已勾选）
                  </p>
                  <div className="flex max-h-96 flex-col gap-2 overflow-y-auto pr-1">
                    {permGroups.map(([groupName, items]) => (
                      <div key={groupName}>
                        <p className="mb-1 text-xs font-semibold text-slate-400">{groupName}</p>
                        <div className="grid grid-cols-2 gap-1">
                          {items.map((p) => (
                            <label key={p.id} className="flex items-center gap-1.5 text-xs text-slate-600 dark:text-slate-300">
                              <input
                                type="checkbox"
                                checked={permDraft.includes(p.id)}
                                onChange={() => togglePerm(p.id)}
                                className="accent-indigo-500"
                              />
                              {p.name}
                            </label>
                          ))}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
                <div>
                  <p className="mb-2 text-xs font-medium text-slate-500 dark:text-slate-400">
                    成员（{memberDraft.length} 人）
                  </p>
                  <div className="flex max-h-96 flex-col gap-1 overflow-y-auto pr-1">
                    {users.map((u) => (
                        <label key={u.id} className="flex items-center gap-1.5 text-xs text-slate-600 dark:text-slate-300">
                          <input
                            type="checkbox"
                            checked={memberDraft.includes(Number(u.id))}
                            onChange={() => toggleMember(Number(u.id))}
                            className="accent-indigo-500"
                          />
                          {u.realName || u.username}
                          <span className="text-slate-400">({u.username})</span>
                        </label>
                      ))}
                  </div>
                </div>
              </div>
              <div className="mt-5 flex gap-2">
                <button
                  disabled={busy}
                  onClick={() => save('permissions')}
                  className="rounded-lg bg-indigo-500 px-4 py-1.5 text-sm font-medium text-white hover:bg-indigo-600 disabled:opacity-50"
                >
                  保存权限
                </button>
                <button
                  disabled={busy}
                  onClick={() => save('members')}
                  className="rounded-lg bg-slate-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-50"
                >
                  保存成员
                </button>
              </div>
            </>
          )}
        </div>
      </div>
      {error && <p className="text-sm text-rose-500">{error}</p>}
    </div>
  )
}
