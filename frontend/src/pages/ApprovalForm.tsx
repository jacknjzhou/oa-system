import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { getTaskDetail, completeTask, rejectTask, transferTask } from '../api/task'
import type { TaskDetail } from '../types'

export default function ApprovalForm() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [detail, setDetail] = useState<TaskDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [comment, setComment] = useState('')
  const [rejectToNode, setRejectToNode] = useState('')
  const [transferUserId, setTransferUserId] = useState('')
  const [actionLoading, setActionLoading] = useState(false)
  const [showReject, setShowReject] = useState(false)
  const [showTransfer, setShowTransfer] = useState(false)

  useEffect(() => {
    fetchDetail()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  const fetchDetail = async () => {
    setLoading(true)
    try {
      if (!id) return
      const data = await getTaskDetail(id)
      setDetail(data)
    } catch (err) {
      console.error('Failed to fetch task detail:', err)
    } finally {
      setLoading(false)
    }
  }

  const handleApprove = async () => {
    if (!id) return
    setActionLoading(true)
    try {
      await completeTask(id, comment || '同意')
      navigate('/')
    } catch (err) {
      console.error('Failed to approve task:', err)
    } finally {
      setActionLoading(false)
    }
  }

  const handleReject = async () => {
    if (!id) return
    setActionLoading(true)
    try {
      await rejectTask(id, comment, rejectToNode || 'start')
      navigate('/')
    } catch (err) {
      console.error('Failed to reject task:', err)
    } finally {
      setActionLoading(false)
    }
  }

  const handleTransfer = async () => {
    if (!id || !transferUserId) return
    setActionLoading(true)
    try {
      await transferTask(id, transferUserId, comment || '转办')
      navigate('/')
    } catch (err) {
      console.error('Failed to transfer task:', err)
    } finally {
      setActionLoading(false)
    }
  }

  if (loading) {
    return <div className="loading">加载中...</div>
  }

  if (!detail) {
    return <div className="empty">未找到任务信息</div>
  }

  const fileIcon = (type: string) => {
    if (type.includes('pdf')) return '📄'
    if (type.includes('image')) return '🖼️'
    if (type.includes('word')) return '📝'
    return '📎'
  }

  return (
    <div>
      {/* 顶部导航栏 */}
      <nav className="navbar">
        <div className="navbar-inner">
          <div className="navbar-logo">
            <button className="btn" onClick={() => navigate('/')}>
              ← 返回列表
            </button>
          </div>
          <span style={{ fontSize: '16px', fontWeight: 600 }}>{detail.title}</span>
          <span className="text-tertiary">{detail.serialNo}</span>
        </div>
      </nav>

      <div className="page-container">
        <div style={{ display: 'flex', gap: '24px', alignItems: 'flex-start' }}>
          {/* 左侧：表单详情 */}
          <div style={{ flex: 1 }}>
            {/* 基本信息 */}
            <div className="card mb-24">
              <div className="card-header">基本信息</div>
              <div className="card-body">
                <table className="table" style={{ background: 'transparent' }}>
                  <tbody>
                    <tr>
                      <td style={{ width: '120px', color: '#8c8c8c', background: 'var(--bg-page)' }}>
                        标题
                      </td>
                      <td colSpan={3}>{detail.title}</td>
                    </tr>
                    <tr>
                      <td style={{ width: '120px', color: '#8c8c8c', background: 'var(--bg-page)' }}>
                        流水号
                      </td>
                      <td>{detail.serialNo}</td>
                      <td style={{ width: '120px', color: '#8c8c8c', background: 'var(--bg-page)' }}>
                        任务类型
                      </td>
                      <td>{detail.type}</td>
                    </tr>
                    <tr>
                      <td style={{ width: '120px', color: '#8c8c8c', background: 'var(--bg-page)' }}>
                        发起人
                      </td>
                      <td>{detail.initiator}</td>
                      <td style={{ width: '120px', color: '#8c8c8c', background: 'var(--bg-page)' }}>
                        发起部门
                      </td>
                      <td>{detail.initiatorDept}</td>
                    </tr>
                    <tr>
                      <td style={{ width: '120px', color: '#8c8c8c', background: 'var(--bg-page)' }}>
                        金额
                      </td>
                      <td style={{ fontWeight: 600, color: '#f5222d' }}>
                        {detail.amount > 0 ? `¥${detail.amount.toLocaleString()}` : '-'}
                      </td>
                      <td style={{ width: '120px', color: '#8c8c8c', background: 'var(--bg-page)' }}>
                        截止时间
                      </td>
                      <td>{detail.deadline}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            {/* 业务明细 */}
            <div className="card mb-24">
              <div className="card-header">业务明细</div>
              <div className="card-body">
                <table className="table">
                  <tbody>
                    {detail.businessDetails.map((item) => (
                      <tr key={item.id}>
                        <td style={{ width: '150px', color: '#8c8c8c', background: 'var(--bg-page)' }}>
                          {item.label}
                        </td>
                        <td>{item.value}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            {/* 附件列表 */}
            <div className="card mb-24">
              <div className="card-header">附件列表</div>
              <div className="card-body">
                {detail.attachments.length === 0 ? (
                  <div className="empty" style={{ padding: '24px' }}>
                    暂无附件
                  </div>
                ) : (
                  <table className="table">
                    <thead>
                      <tr>
                        <th>文件名</th>
                        <th>大小</th>
                        <th>类型</th>
                        <th>上传时间</th>
                        <th>操作</th>
                      </tr>
                    </thead>
                    <tbody>
                      {detail.attachments.map((file) => (
                        <tr key={file.id}>
                          <td>
                            {fileIcon(file.type)} {file.name}
                          </td>
                          <td>{(file.size / 1024).toFixed(1)} KB</td>
                          <td>{file.type}</td>
                          <td>{file.uploadedAt}</td>
                          <td>
                            <button className="btn">下载</button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            </div>
          </div>

          {/* 右侧：审批流程链 */}
          <div style={{ width: '320px', flexShrink: 0 }}>
            <div className="card">
              <div className="card-header">审批流程</div>
              <div className="card-body" style={{ padding: '16px' }}>
                {detail.approvalChain.map((node, index) => (
                  <div
                    key={node.id}
                    className="flex gap-12"
                    style={{
                      position: 'relative',
                      paddingBottom: index === detail.approvalChain.length - 1 ? 0 : 16,
                    }}
                  >
                    {/* 连接线 + 圆点 */}
                    <div
                      style={{
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                        flexShrink: 0,
                      }}
                    >
                      <div
                        style={{
                          width: '32px',
                          height: '32px',
                          borderRadius: '50%',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          fontSize: '14px',
                          fontWeight: 600,
                          background:
                            node.status === 'completed'
                              ? '#52c41a'
                              : node.status === 'current'
                                ? '#1890ff'
                                : '#f0f0f0',
                          color: node.status === 'completed' || node.status === 'current' ? '#fff' : '#8c8c8c',
                        }}
                      >
                        {node.status === 'completed' ? '✓' : index + 1}
                      </div>
                      {index < detail.approvalChain.length - 1 && (
                        <div
                          style={{
                            width: '2px',
                            flex: 1,
                            minHeight: '24px',
                            background:
                              node.status === 'completed' ? '#52c41a' : '#f0f0f0',
                            marginTop: '4px',
                          }}
                        />
                      )}
                    </div>

                    {/* 节点内容 */}
                    <div style={{ flex: 1 }}>
                      <div className="font-bold" style={{ fontSize: '14px' }}>
                        {node.name}
                      </div>
                      <div className="text-tertiary text-sm" style={{ marginTop: '2px' }}>
                        {node.approver}
                      </div>
                      {node.action && (
                        <span
                          className={`tag tag-${node.status === 'completed' ? 'completed' : 'processing'}`}
                          style={{ marginTop: '4px' }}
                        >
                          {node.action}
                        </span>
                      )}
                      {node.comment && (
                        <div
                          className="text-sm"
                          style={{
                            marginTop: '4px',
                            color: '#595959',
                            background: '#fafafa',
                            padding: '6px 8px',
                            borderRadius: '4px',
                          }}
                        >
                          "{node.comment}"
                        </div>
                      )}
                      {node.time && (
                        <div className="text-tertiary text-sm" style={{ marginTop: '4px' }}>
                          {node.time}
                        </div>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>

        {/* 底部操作栏 */}
        <div className="card mt-24">
          <div className="card-header">审批操作</div>
          <div className="card-body">
            {/* 审批意见输入框 */}
            <div className="mb-16">
              <label style={{ display: 'block', marginBottom: '8px', fontWeight: 500 }}>
                审批意见
              </label>
              <textarea
                className="textarea"
                style={{ minHeight: '80px' }}
                placeholder="请输入审批意见..."
                value={comment}
                onChange={(e) => setComment(e.target.value)}
              />
            </div>

            {/* 驳回节点选择 */}
            {showReject && (
              <div className="mb-16" style={{ background: 'var(--danger-light)', padding: '16px', borderRadius: '6px' }}>
                <label style={{ display: 'block', marginBottom: '8px', fontWeight: 500, color: '#f5222d' }}>
                  驳回至节点
                </label>
                <select
                  className="select"
                  value={rejectToNode}
                  onChange={(e) => setRejectToNode(e.target.value)}
                >
                  <option value="">请选择驳回节点</option>
                  <option value="start">发起人（重新填写）</option>
                  <option value="dept_manager">部门经理</option>
                  <option value="finance">财务</option>
                </select>
              </div>
            )}

            {/* 转办用户输入 */}
            {showTransfer && (
              <div className="mb-16" style={{ background: 'var(--info-light)', padding: '16px', borderRadius: '6px' }}>
                <label style={{ display: 'block', marginBottom: '8px', fontWeight: 500, color: '#1890ff' }}>
                  转办给
                </label>
                <input
                  type="text"
                  className="input"
                  placeholder="请输入用户ID或姓名"
                  value={transferUserId}
                  onChange={(e) => setTransferUserId(e.target.value)}
                />
              </div>
            )}

            {/* 操作按钮 */}
            <div className="flex gap-12">
              <button
                className="btn btn-success"
                onClick={handleApprove}
                disabled={actionLoading}
              >
                ✓ 通过
              </button>
              <button
                className="btn btn-danger"
                onClick={() => {
                  setShowReject(!showReject)
                  setShowTransfer(false)
                  if (showReject) handleReject()
                }}
                disabled={actionLoading}
              >
                ✗ 驳回
              </button>
              <button
                className="btn"
                onClick={() => {
                  setShowTransfer(!showTransfer)
                  setShowReject(false)
                  if (showTransfer) handleTransfer()
                }}
                disabled={actionLoading}
              >
                转办
              </button>
              <button
                className="btn"
                onClick={() => navigate(`/tracking/${detail.id}`)}
              >
                查看流程
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
