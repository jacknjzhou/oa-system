import apiClient from './client'
import type { ApiResponse, Document } from '../types'

// Mock 公文列表
const mockDocuments: Document[] = [
  {
    id: '1',
    serialNo: 'GW2024-001',
    title: '关于2026年度预算编制的通知',
    type: '通知',
    category: '财务',
    status: 'published',
    author: '财务部',
    department: '财务部',
    content: '各部门请于9月15日前提交2026年度预算方案...',
    createdAt: '2026-08-20 09:00',
    updatedAt: '2026-08-20 09:00',
  },
  {
    id: '2',
    serialNo: 'GW2024-002',
    title: '关于调整办公时间的通告',
    type: '通告',
    category: '行政',
    status: 'published',
    author: '行政部',
    department: '行政部',
    content: '经公司研究决定，自2026年9月1日起调整办公时间...',
    createdAt: '2026-08-22 14:00',
    updatedAt: '2026-08-22 14:00',
  },
  {
    id: '3',
    serialNo: 'GW2024-003',
    title: '季度工作总结报告模板',
    type: '报告',
    category: '综合',
    status: 'draft',
    author: '总经理办公室',
    department: '总经办',
    content: '请各部门按照此模板撰写季度工作总结...',
    createdAt: '2026-08-25 10:00',
    updatedAt: '2026-08-25 10:00',
  },
]

// 获取公文列表
export async function getDocuments(): Promise<Document[]> {
  try {
    const response = await apiClient.get<ApiResponse<Document[]>>('/documents')
    return response.data.data
  } catch {
    return mockDocuments
  }
}

// 获取公文详情
export async function getDocument(id: string): Promise<Document> {
  try {
    const response = await apiClient.get<ApiResponse<Document>>(`/documents/${id}`)
    return response.data.data
  } catch {
    const doc = mockDocuments.find((d) => d.id === id) || mockDocuments[0]
    return { ...doc, id }
  }
}

// 创建公文
export async function createDocument(
  data: Omit<Document, 'id' | 'serialNo' | 'createdAt' | 'updatedAt'>
): Promise<Document> {
  try {
    const response = await apiClient.post<ApiResponse<Document>>('/documents', data)
    return response.data.data
  } catch {
    const now = new Date().toISOString()
    return {
      ...data,
      id: 'doc-' + Date.now(),
      serialNo: 'GW2024-' + String(Date.now()).slice(-3),
      createdAt: now,
      updatedAt: now,
    }
  }
}

// 更新公文
export async function updateDocument(id: string, data: Partial<Document>): Promise<Document> {
  try {
    const response = await apiClient.put<ApiResponse<Document>>(`/documents/${id}`, data)
    return response.data.data
  } catch {
    const doc = mockDocuments.find((d) => d.id === id) || mockDocuments[0]
    return { ...doc, ...data, updatedAt: new Date().toISOString() }
  }
}

// 归档公文
export async function archiveDocument(id: string): Promise<void> {
  try {
    await apiClient.post(`/documents/${id}/archive`)
  } catch {
    // Mock: 静默成功
  }
}
