import apiClient from './client'
import type { DocumentDTO } from '../types'

export interface DocumentPayload {
  title: string
  content: string
  docType: string
  urgency: string
  secrecyLevel: string
}

/** 公文列表（可选标题模糊搜索） */
export async function getDocuments(title?: string): Promise<DocumentDTO[]> {
  const response = await apiClient.get<DocumentDTO[]>('/documents', {
    params: title ? { title } : undefined,
  })
  return response.data
}

export async function getDocument(id: number): Promise<DocumentDTO> {
  const response = await apiClient.get<DocumentDTO>(`/documents/${id}`)
  return response.data
}

export async function createDocument(payload: DocumentPayload): Promise<DocumentDTO> {
  const response = await apiClient.post<DocumentDTO>('/documents', payload)
  return response.data
}

export async function updateDocument(id: number, payload: DocumentPayload): Promise<DocumentDTO> {
  const response = await apiClient.put<DocumentDTO>(`/documents/${id}`, payload)
  return response.data
}

export async function archiveDocument(id: number): Promise<DocumentDTO> {
  const response = await apiClient.post<DocumentDTO>(`/documents/${id}/archive`)
  return response.data
}
