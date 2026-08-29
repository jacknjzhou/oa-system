import { useState, useEffect, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { getTasks } from '../api/task'
import { getCachedUser, logout } from '../api/auth'
import type { Task, Priority, TaskStatus } from '../types'

const statusFilters = [
  { label: '全部', value: 'all' },
  { label: '待处理', value: 'pending' },
  { label: '处理中', value: 'processing' },
  { label: '已逾期', value: 'overdue' },
]

const priorityLabels: Record<Priority, string> = {
  low: '低',
  normal: '中',
  high: '高',
  urgent: '紧急',
}

const statusLabels: Record<TaskStatus, string> = {
  pending: '待处理',
  processing: '处理中',
  completed: '已完成',
  rejected: '已驳回',
  overdue: '已逾期',
}

const PAGE_SIZE = 8

export default function TodoList() {
  const navigate = useNavigate()
  const [tasks, setTasks] = useState<Task[]>([])
  const [loading, setLoading] = useState(true)
  const [statusFilter, setStatusFilter] = useState('all')
  const [search, setSearch] = useState('')
  const [currentPage, setCurrentPage] = useState(1)
  const user = getCachedUser()

  useEffect(() => {
    fetchTasks()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statusFilter])

  const fetchTasks = async () => {
    setLoading(true)
    try {
      const data = await getTasks(statusFilter)
      setTasks(data)
    } catch (err) {
      console.error('Failed to fetch tasks:', err)
    } finally {
      setLoading(false)
    }
  }

  // 搜索过滤
  const filteredTasks = useMemo(() => {
    if (!search) return tasks
    const q = search.toLowerCase()
    return tasks.filter(
      (t) =>
        t.title.toLowerCase().includes(q) ||
        t.serialNo.toLowerCase().includes(q) ||
        t.initiator.toLowerCase().includes(q) ||
        t.type.toLowerCase().includes(q)
    )
  }, [tasks, search])

  // 分页
  const totalPages = Math.max(1, Math.ceil(filteredTasks.length / PAGE_SIZE))
  const pagedTasks = filteredTasks.slice(
    (currentPage - 1) * PAGE_SIZE,
    currentPage * PAGE_SIZE
  )

  const handleSearch = (e: React.ChangeEvent<HTMLInputElement>) => {
    setSearch(e.target.value)
    setCurrentPage(1)
  }

  const handleLogout = async () => {
    await logout()
    navigate('/login')
  }

  return (
    <div>
      {/* 顶部导航栏 */}
      <nav className="navbar">
        <div className="navbar-inner">
          <div className="navbar-logo">
            <span>OA 审批系统</span>
          </div>
          <div className="navbar-user">
            <div className="navbar-avatar">
              {user?.nickname?.charAt(0) || 'A'}
            </div>
            <span>{user?.nickname || '管理员'}</span>
            <button className="btn" onClick={handleLogout}>
              退出
            </button>
          </div>
        </div>
      </nav>

      <div className="page-container">
        {/* 筛选栏 */}
        <div className="card mb-24">
          <div className="card-body" style={{ padding: '16px 24px' }}>
            <div className="flex items-center justify-between">
              <div className="flex gap-8">
                {statusFilters.map((f) => (
                  <button
                    key={f.value}
                    className={`btn ${statusFilter === f.value ? 'btn-primary' : ''}`}
                    onClick={() => {
                      setStatusFilter(f.value)
                      setCurrentPage(1)
                    }}
                  >
                    {f.label}
                  </button>
                ))}
              </div>
              <input
                type="text"
                className="input"
                style={{ width: '260px' }}
                placeholder="搜索标题、流水号、发起人..."
                value={search}
                onChange={handleSearch}
              />
            </div>
          </div>
        </div>

        {/* 任务卡片列表 */}
        {loading ? (
          <div className="loading">加载中...</div>
        ) : pagedTasks.length === 0 ? (
          <div className="empty">暂无待办任务</div>
        ) : (
          <div>
            {pagedTasks.map((task) => (
              <div
                key={task.id}
                className="card mb-16"
                style={{ cursor: 'pointer', transition: 'box-shadow 0.2s' }}
                onClick={() => navigate(`/task/${task.id}`)}
                onMouseEnter={(e) => {
                  e.currentTarget.style.boxShadow = '0 4px 16px rgba(0,0,0,0.08)'
                }}
                onMouseLeave={(e) => {
                  e.currentTarget.style.boxShadow = '0 2px 8px rgba(0,0,0,0.06)'
                }}
              >
                <div className="card-body" style={{ padding: '20px 24px' }}>
                  <div className="flex items-center justify-between mb-16">
                    <div className="flex items-center gap-12">
                      <h3 style={{ fontSize: '16px', fontWeight: 600 }}>{task.title}</h3>
                      <span className={`tag tag-${task.priority}`}>
                        {priorityLabels[task.priority]}
                      </span>
                      <span className={`tag tag-${task.status}`}>
                        {statusLabels[task.status]}
                      </span>
                    </div>
                    <span className="text-tertiary text-sm">{task.serialNo}</span>
                  </div>

                  <div
                    style={{
                      display: 'grid',
                      gridTemplateColumns: 'repeat(4, 1fr)',
                      gap: '12px',
                      color: '#595959',
                      fontSize: '13px',
                    }}
                  >
                    <div>
                      <span className="text-tertiary">发起人：</span>
                      {task.initiator}
                    </div>
                    <div>
                      <span className="text-tertiary">部门：</span>
                      {task.initiatorDept}
                    </div>
                    <div>
                      <span className="text-tertiary">类型：</span>
                      {task.type}
                    </div>
                    <div>
                      <span className="text-tertiary">金额：</span>
                      {task.amount > 0 ? `¥${task.amount.toLocaleString()}` : '-'}
                    </div>
                    <div>
                      <span className="text-tertiary">当前节点：</span>
                      {task.currentNode}
                    </div>
                    <div>
                      <span className="text-tertiary">截止时间：</span>
                      <span style={{ color: task.status === 'overdue' ? '#f5222d' : '#595959' }}>
                        {task.deadline}
                      </span>
                    </div>
                    <div>
                      <span className="text-tertiary">创建时间：</span>
                      {task.createdAt}
                    </div>
                  </div>
                </div>
              </div>
            ))}

            {/* 分页 */}
            {filteredTasks.length > PAGE_SIZE && (
              <div className="pagination">
                <button
                  className="btn"
                  onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
                  disabled={currentPage === 1}
                >
                  上一页
                </button>
                <span className="page-info">
                  第 {currentPage} / {totalPages} 页（共 {filteredTasks.length} 条）
                </span>
                <button
                  className="btn"
                  onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
                  disabled={currentPage === totalPages}
                >
                  下一页
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
