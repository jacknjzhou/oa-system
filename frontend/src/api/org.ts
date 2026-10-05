import apiClient from './client'

/** 部门树节点（memberCount = 直属在职员工数，查询时实时派生） */
export interface OrgNode {
  id: number
  orgCode: string
  orgName: string
  parentId: number | null
  sortOrder: number | null
  status: string
  memberCount: number
  children: OrgNode[]
}

export type OrgForm = {
  orgName: string
  orgCode?: string | null
  parentId?: number | null
  /** 编辑时清除上级（升为顶级） */
  clearParent?: boolean
  sortOrder?: number | null
}

/** 部门管理（SY-02）。读端点任意登录用户；写端点需 hr:dept。 */
export const orgApi = {
  tree: () => apiClient.get<OrgNode[]>('/organizations/tree').then((r) => r.data),
  create: (b: OrgForm) => apiClient.post<OrgNode>('/organizations', b).then((r) => r.data),
  update: (id: number, b: Partial<OrgForm>) =>
    apiClient.put<OrgNode>(`/organizations/${id}`, b).then((r) => r.data),
  remove: (id: number) => apiClient.delete(`/organizations/${id}`).then((r) => r.data),
}
