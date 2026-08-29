import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { getProcessInstance, cancelProcessInstance } from '../api/process'
import type { ProcessInstance } from '../types'

export default function ProcessTracking() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [instance, setInstance] = useState<ProcessInstance | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    fetchInstance()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  const fetchInstance = async () => {
    setLoading(true)
    try {
      if (!id) return
      const data = await getProcessInstance(id)
      setInstance(data)
    } catch (err) {
      console.error('Failed to fetch process instance:', err)
    } finally {
      setLoading(false)
    }
  }

  const handleCancel = async () => {
    if (!instance) return
    if (!confirm('确定要取消此流程实例吗？')) return
    await cancelProcessInstance(instance.id)
    navigate('/')
  }

  if (loading) {
    return <div className="loading">加载中...</div>
  }

  if (!instance) {
    return <div className="empty">未找到流程信息</div>
  }

  const statusLabels: Record<string, string> = {
    running: '进行中',
    completed: '已完成',
    canceled: '已取消',
    rejected: '已驳回',
  }

  const actionLabels: Record<string, string> = {
    '提交申请': '提交申请',
    '同意': '同意',
    '驳回': '驳回',
    '转办': '转办',
    '审批中': '审批中',
    '取消': '取消',
  }

  return (
    <div>
      {/* 顶部导航栏 */}
      <nav className="navbar">
        <div className="navbar-inner">
          <div className="navbar-logo">
            <button className="btn" onClick={() => navigate(-1)}>
              ← 返回
            </button>
          </div>
          <span style={{ fontSize: '16px', fontWeight: 600 }}>流程跟踪</span>
          <span className="text-tertiary">{instance.serialNo}</span>
        </div>
      </nav>

      <div className="page-container">
        {/* 顶部：流程进度图 */}
        <div className="card mb-24">
          <div className="card-header">流程进度</div>
          <div className="card-body" style={{ padding: '32px 24px' }}>
            <div className="process-flow">
              {instance.nodes.map((node, index) => (
                <div key={node.id} style={{ display: 'flex', alignItems: 'center', flex: 1 }}>
                  <div className={`process-node process-node-${node.status}`}>
                    <div className="process-node-circle">
                      {node.status === 'completed' ? '✓' : index + 1}
                    </div>
                    <div className="process-node-label">{node.name}</div>
                    {node.status === 'current' && (
                      <div className="text-tertiary text-sm" style={{ marginTop: '2px' }}>
                        {node.assignee || '待处理'}
                      </div>
                    )}
                    {node.status === 'completed' && node.operator && (
                      <div className="text-tertiary text-sm" style={{ marginTop: '2px' }}>
                        {node.operator}
                      </div>
                    )}
                  </div>
                  {index < instance.nodes.length - 1 && (
                    <div
                      className={`process-line ${
                        node.status === 'completed' ? 'process-line-completed' : ''
                      }`}
                    />
                  )}
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* 中间：实例信息卡片 */}
        <div className="card mb-24">
          <div className="card-header">实例信息</div>
          <div className="card-body">
            <table className="table">
              <tbody>
                <tr>
                  <td style={{ width: '120px', color: '#8c8c8c', background: 'var(--bg-page)' }}>
                    流水号
                  </td>
                  <td style={{ fontWeight: 600 }}>{instance.serialNo}</td>
                  <td style={{ width: '120px', color: '#8c8c8c', background: 'var(--bg-page)' }}>
                    流程状态
                  </td>
                  <td>
                    <span className={`tag tag-${instance.status}`}>
                      {statusLabels[instance.status] || instance.status}
                    </span>
                  </td>
                </tr>
                <tr>
                  <td style={{ width: '120px', color: '#8c8c8c', background: 'var(--bg-page)' }}>
                    流程名称
                  </td>
                  <td>{instance.definitionName}</td>
                  <td style={{ width: '120px', color: '#8c8c8c', background: 'var(--bg-page)' }}>
                    发起人
                  </td>
                  <td>{instance.initiator}</td>
                </tr>
                <tr>
                  <td style={{ width: '120px', color: '#8c8c8c', background: 'var(--bg-page)' }}>
                    当前节点
                  </td>
                  <td>
                    {instance.currentNode || '已完成'}
                  </td>
                  <td style={{ width: '120px', color: '#8c8c8c', background: 'var(--bg-page)' }}>
                    耗时
                  </td>
                  <td>{instance.duration || '-'}</td>
                </tr>
                <tr>
                  <td style={{ width: '120px', color: '#8c8c8c', background: 'var(--bg-page)' }}>
                    启动时间
                  </td>
                  <td>{instance.startTime}</td>
                  <td style={{ width: '120px', color: '#8c8c8c', background: 'var(--bg-page)' }}>
                    结束时间
                  </td>
                  <td>{instance.endTime || '-'}</td>
                </tr>
                <tr>
                  <td style={{ width: '120px', color: '#8c8c8c', background: 'var(--bg-page)' }}>
                    标题
                  </td>
                  <td colSpan={3}>{instance.title}</td>
                </tr>
              </tbody>
            </table>

            {instance.status === 'running' && (
              <div className="mt-16">
                <button className="btn btn-danger" onClick={handleCancel}>
                  取消流程
                </button>
              </div>
            )}
          </div>
        </div>

        {/* 底部：审批轨迹时间线 */}
        <div className="card">
          <div className="card-header">审批轨迹</div>
          <div className="card-body">
            <div className="timeline">
              {instance.records.map((record, index) => (
                <div key={record.id} className="timeline-item">
                  <div
                    className={`timeline-dot ${
                      record.action === '驳回'
                        ? 'timeline-dot-rejected'
                        : record.action === '审批中'
                          ? ''
                          : 'timeline-dot-completed'
                    }`}
                  />
                  <div className="timeline-content">
                    <div className="timeline-title">
                      第{index + 1}步 - {record.node}
                    </div>
                    <div className="flex gap-8" style={{ flexWrap: 'wrap' }}>
                      <span className="text-secondary">
                        操作人：<strong>{record.operator}</strong>
                      </span>
                      <span className={`tag tag-${record.action === '同意' ? 'completed' : record.action === '驳回' ? 'rejected' : 'processing'}`}>
                        {actionLabels[record.action] || record.action}
                      </span>
                      <span className="timeline-meta">{record.time}</span>
                    </div>
                    {record.comment && (
                      <div
                        style={{
                          marginTop: '6px',
                          background: '#fafafa',
                          padding: '8px 12px',
                          borderRadius: '4px',
                          color: '#595959',
                        }}
                      >
                        审批意见：{record.comment}
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
