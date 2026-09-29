import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  getMyNotifications,
  getUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
} from '../api/notification'
import { useToast } from './Toast'
import { formatRelativeTime } from '../utils/format'
import type { Notification } from '../types'

const POLL_INTERVAL_MS = 30_000
const PANEL_LIMIT = 20

/** 顶栏铃铛：未读角标（30 秒轮询）+ 通知下拉面板（点击跳转对应页面） */
export default function NotificationBell() {
  const navigate = useNavigate()
  const { showToast } = useToast()
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState<Notification[]>([])
  const [unreadCount, setUnreadCount] = useState(0)
  const containerRef = useRef<HTMLDivElement>(null)

  const refreshCount = useCallback(async () => {
    try {
      setUnreadCount(await getUnreadCount())
    } catch {
      // 轮询失败静默（401 已由全局拦截器跳转登录）
    }
  }, [])

  useEffect(() => {
    refreshCount()
    const timer = setInterval(refreshCount, POLL_INTERVAL_MS)
    return () => clearInterval(timer)
  }, [refreshCount])

  // 点击面板外关闭
  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', onDown)
    return () => document.removeEventListener('mousedown', onDown)
  }, [open])

  const openPanel = async () => {
    setOpen((prev) => !prev)
    try {
      const [list, count] = await Promise.all([getMyNotifications(PANEL_LIMIT), getUnreadCount()])
      setItems(list)
      setUnreadCount(count)
    } catch (e) {
      showToast(e instanceof Error ? e.message : '加载通知失败', 'error')
    }
  }

  const handleItemClick = async (n: Notification) => {
    try {
      if (!n.isRead) {
        await markNotificationRead(n.id)
        const now = new Date().toISOString()
        setItems((prev) =>
          prev.map((i) => (i.id === n.id ? { ...i, isRead: true, readAt: now } : i))
        )
        setUnreadCount((c) => Math.max(0, c - 1))
      }
    } catch (e) {
      showToast(e instanceof Error ? e.message : '操作失败', 'error')
    }
    setOpen(false)
    if (n.refType === 'TASK' && n.refId) {
      navigate(`/task/${n.refId}`)
    } else if (n.refType === 'PROCESS_INSTANCE' && n.refId) {
      navigate(`/tracking/${n.refId}`)
    }
    // DOCUMENT 等无对应页面：仅标记已读并关闭
  }

  const handleMarkAll = async () => {
    try {
      await markAllNotificationsRead()
      const now = new Date().toISOString()
      setItems((prev) => prev.map((i) => ({ ...i, isRead: true, readAt: i.readAt ?? now })))
      setUnreadCount(0)
    } catch (e) {
      showToast(e instanceof Error ? e.message : '操作失败', 'error')
    }
  }

  return (
    <div className="relative" ref={containerRef}>
      <button
        type="button"
        onClick={openPanel}
        title="通知"
        className="relative rounded-lg p-2 text-slate-500 transition-colors hover:bg-slate-100 hover:text-slate-700 dark:text-slate-400 dark:hover:bg-slate-700 dark:hover:text-slate-200"
      >
        <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M14.857 17.082a23.848 23.848 0 005.454-1.31A8.967 8.967 0 0118 9.75v-.7V9A6 6 0 006 9v.75a8.967 8.967 0 01-2.312 6.022c1.733.64 3.56 1.085 5.455 1.31m5.714 0a24.255 24.255 0 01-5.714 0m5.714 0a3 3 0 11-5.714 0"
          />
        </svg>
        {unreadCount > 0 && (
          <span className="absolute -right-1 -top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-red-500 px-1 text-[10px] font-bold leading-none text-white">
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 top-full z-50 mt-2 w-80 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-lifted dark:border-slate-700 dark:bg-slate-800">
          <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3 dark:border-slate-700">
            <p className="text-sm font-semibold text-slate-900 dark:text-slate-100">
              通知
              {unreadCount > 0 && (
                <span className="ml-1.5 text-xs font-normal text-slate-400 dark:text-slate-500">
                  {unreadCount} 条未读
                </span>
              )}
            </p>
            {unreadCount > 0 && (
              <button
                type="button"
                onClick={handleMarkAll}
                className="text-xs text-primary-600 hover:text-primary-700 dark:text-primary-400"
              >
                全部已读
              </button>
            )}
          </div>

          <ul className="max-h-96 overflow-y-auto">
            {items.length === 0 ? (
              <li className="px-4 py-8 text-center text-sm text-slate-400 dark:text-slate-500">
                暂无通知
              </li>
            ) : (
              items.map((n) => (
                <li key={n.id}>
                  <button
                    type="button"
                    onClick={() => handleItemClick(n)}
                    className={`block w-full border-b border-slate-50 px-4 py-3 text-left transition-colors last:border-0 hover:bg-slate-50 dark:border-slate-700/50 dark:hover:bg-slate-700/40 ${
                      n.isRead ? 'text-slate-500 dark:text-slate-400' : ''
                    }`}
                  >
                    <span className="flex items-start gap-2">
                      <span
                        className={`mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full ${
                          n.isRead ? 'bg-transparent' : 'bg-primary-500'
                        }`}
                      />
                      <span className="min-w-0 flex-1">
                        <span
                          className={`block truncate text-sm ${
                            n.isRead
                              ? 'font-normal text-slate-600 dark:text-slate-400'
                              : 'font-medium text-slate-900 dark:text-slate-100'
                          }`}
                        >
                          {n.title}
                        </span>
                        {n.content && (
                          <span className="mt-0.5 block truncate text-xs text-slate-400 dark:text-slate-500">
                            {n.content}
                          </span>
                        )}
                        <span className="mt-1 block text-xs text-slate-400 dark:text-slate-500">
                          {formatRelativeTime(n.createdAt)}
                        </span>
                      </span>
                    </span>
                  </button>
                </li>
              ))
            )}
          </ul>
        </div>
      )}
    </div>
  )
}
