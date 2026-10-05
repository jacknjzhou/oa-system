import apiClient from './client'

export type LeaveUnit = 'day' | 'hour' | 'half_day'

export interface LeaveType {
  id: number
  code: string
  name: string
  category: string
  quotaType: 'fixed' | 'accrual' | 'none'
  limited: boolean
  annualQuota: number
  unit: LeaveUnit
  weight: number
  enabled: boolean
}

export interface LeaveBalance {
  code: string
  name: string
  quotaType: string
  limited: boolean
  unit: LeaveUnit
  weight: number
  quota: number | null
  used: number | null
  frozen: number | null
  available: number | null
}

export interface LeaveTransaction {
  id: number
  typeCode: string
  typeName: string
  unit: LeaveUnit
  delta: number
  txnType: 'GRANT' | 'ADJUST' | 'QUOTA' | 'CONSUME' | 'REVERSE' | 'FREEZE' | 'RELEASE' | string
  typeLabel: string
  operatorId: number | null
  operatorName: string | null
  acceptName: string | null
  reason: string | null
  remark: string | null
  refInstanceNo: string | null
  instanceId: number | null
  createdAt: string
}

export interface LeaveLedgerRow extends LeaveBalance {
  transactions: LeaveTransaction[]
}

/** 假期类型 CRUD 请求体 */
export interface LeaveTypeRequest {
  code?: string | null
  name: string
  category: string
  quotaType: 'fixed' | 'accrual' | 'none'
  annualQuota?: number | null
  unit?: LeaveUnit | null
  weight?: number | null
  enabled?: boolean | null
}

/** 假期管理（HD-01/02/03/08） */
export interface AdminDepartment {
  id: number
  orgCode: string
  orgName: string
  memberCount: number
}

export interface AdminEmployee {
  id: number
  username: string
  realName: string | null
  orgId: number | null
  orgName: string | null
  position: string | null
  balances: LeaveBalance[]
}

export interface AdminLogPage {
  total: number
  items: LeaveTransaction[]
}

export const leaveApi = {
  types: () => apiClient.get<LeaveType[]>('/leave/types').then((r) => r.data),
  balance: (typeCode: string) =>
    apiClient.get<LeaveBalance>('/leave/balance', { params: { typeCode } }).then((r) => r.data),
  ledger: (typeCode: string) =>
    apiClient.get<LeaveLedgerRow[]>('/leave/ledger', { params: { typeCode } }).then((r) => r.data),

  // 假期类型 CRUD（HD-04~06，leave:manage）
  createType: (body: LeaveTypeRequest) => apiClient.post<LeaveType>('/leave/admin/types', body).then((r) => r.data),
  listAllTypes: () => apiClient.get<LeaveType[]>('/leave/admin/types').then((r) => r.data),
  updateType: (id: number, body: LeaveTypeRequest) =>
    apiClient.put<LeaveType>(`/leave/admin/types/${id}`, body).then((r) => r.data),
  deleteType: (id: number) => apiClient.delete<void>(`/leave/admin/types/${id}`),

  // 假期管理（HD-01/02/03/08，leave:manage）
  adminDepartments: () => apiClient.get<AdminDepartment[]>('/leave/admin/departments').then((r) => r.data),
  adminEmployees: (params: { orgId?: number | null; typeCode?: string | null; keyword?: string | null }) =>
    apiClient
      .get<AdminEmployee[]>('/leave/admin/employees', {
        params: { ...params },
        paramsSerializer: (p: Record<string, string | number | null | undefined>) => {
          const parts: string[] = []
          Object.entries(p).forEach(([k, v]) => {
            if (v === null || v === undefined || v === '') return
            parts.push(`${k}=${encodeURIComponent(String(v))}`)
          })
          return parts.join('&')
        },
      })
      .then((r) => r.data),
  adminBalances: (userId: number) => apiClient.get<LeaveBalance[]>(`/leave/admin/balances/${userId}`).then((r) => r.data),
  adminLogs: (params: { userId?: number | null; typeCode?: string | null; page?: number; size?: number }) =>
    apiClient
      .get<AdminLogPage>('/leave/admin/logs', {
        params,
        paramsSerializer: (p: Record<string, string | number | null | undefined>) => {
          const parts: string[] = []
          Object.entries(p).forEach(([k, v]) => {
            if (v === null || v === undefined || v === '') return
            parts.push(`${k}=${encodeURIComponent(String(v))}`)
          })
          return parts.join('&')
        },
      })
      .then((r) => r.data),

  /** 余额导出 CSV（服务端生成，Blob 下载） */
  exportBalancesCsv: async (params: { orgId?: number | null; typeCode?: string | null; keyword?: string | null }) => {
    const clean: Record<string, string | number> = {}
    Object.entries(params).forEach(([k, v]) => {
      if (v !== null && v !== undefined && v !== '') clean[k] = v
    })
    const resp = await apiClient.get<Blob>('/leave/admin/export', {
      params: clean,
      responseType: 'blob',
    })
    const url = URL.createObjectURL(resp.data)
    const a = document.createElement('a')
    a.href = url
    a.download = `leave-balances-${new Date().toISOString().slice(0, 10)}.csv`
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  },

  /** 授予/调整余额（HD-07）：SET_QUOTA 额度设定 / SET_REMAINING 剩余时长修正 */
  adminGrant: (body: {
    userId: number
    typeCode: string
    action: 'SET_QUOTA' | 'SET_REMAINING'
    amount: number
    remark?: string | null
  }) => apiClient.post<LeaveBalance>('/leave/admin/grant', body).then((r) => r.data),
}
