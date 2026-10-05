import apiClient from './client'

export interface CheckRecordRow {
  id: number
  checkTime: string
  type: 'in' | 'out'
  source: 'manual' | 'scan'
  note: string | null
}

export interface CheckToday {
  date: string
  firstIn: string | null
  lastOut: string | null
  workMinutes: number
  records: CheckRecordRow[]
}

export interface CheckMonthDay {
  date: string
  firstIn: string | null
  lastOut: string | null
  minutes: number
  count: number
}

export interface CheckAllRow {
  id: number
  userId: number
  userName: string | null
  username: string | null
  checkTime: string
  type: 'in' | 'out'
  source: string
  note: string | null
}

/** 打卡（AT-01） */
export const checkApi = {
  clock: (type: 'in' | 'out', source: 'manual' | 'scan' = 'manual', note?: string) =>
    apiClient.post<CheckRecordRow>(`/check/${type}`, { source, note }).then((r) => r.data),
  today: () => apiClient.get<CheckToday>('/check/today').then((r) => r.data),
  month: (date?: string) =>
    apiClient.get<CheckMonthDay[]>('/check/month', { params: date ? { date } : {} }).then((r) => r.data),
  /** 全部考勤（只读）：date=当月任意一天（ISO）；userId 可选 */
  listAll: (date?: string, userId?: number | null) =>
    apiClient
      .get<CheckAllRow[]>('/check/all', {
        params: { ...(date ? { date } : {}), ...(userId ? { userId } : {}) },
      })
      .then((r) => r.data),
}
