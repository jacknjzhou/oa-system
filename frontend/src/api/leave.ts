import apiClient from './client'

export interface LeaveType {
  id: number
  code: string
  name: string
  category: string
  quotaType: 'fixed' | 'accrual' | 'none'
  annualQuota: number
}

export interface LeaveBalance {
  code: string
  name: string
  quotaType: string
  quota: number
  used: number
  frozen: number
  available: number
}

export interface LeaveTransaction {
  delta: number
  reason: string
  refInstanceNo: string | null
  createdAt: string
}

export interface LeaveLedgerRow extends LeaveBalance {
  transactions: LeaveTransaction[]
}

export const leaveApi = {
  types: () => apiClient.get<LeaveType[]>('/leave/types').then((r) => r.data),
  balance: (typeCode: string) =>
    apiClient.get<LeaveBalance>('/leave/balance', { params: { typeCode } }).then((r) => r.data),
  ledger: (typeCode: string) =>
    apiClient.get<LeaveLedgerRow[]>('/leave/ledger', { params: { typeCode } }).then((r) => r.data),
}
