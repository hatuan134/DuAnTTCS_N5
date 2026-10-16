import { apiClient } from '../../core/api/apiClient'

export type AuditActionGroup =
  | 'LOGIN'
  | 'CREATE_ACCOUNT'
  | 'UPDATE_ACCOUNT'
  | 'ISSUE_CARD'
  | 'UPDATE_POLICY'
  | 'OTHER'

export type AuditLogItem = {
  id: number
  timestamp: string
  actorId: number | null
  actor: string
  actorRole: string
  action: string
  actionGroup: AuditActionGroup
  actionLabel: string
  target: string
  targetType: string
  entityType: string | null
  entityId: string | null
  ipAddress: string
  detail: string
}

export type AuditActorOption = {
  id: number
  fullName: string
  email: string
  role: string
}

export type AuditActionOption = {
  value: string
  label: string
}

export type AuditFilterOptions = {
  actors: AuditActorOption[]
  actions: AuditActionOption[]
}

export type AuditSearchParams = {
  fromDate?: string
  toDate?: string
  actorId?: number
  action?: string
  keyword?: string
}

export const auditLogService = {
  search: async (params: AuditSearchParams = {}): Promise<AuditLogItem[]> => {
    const response = await apiClient.get<AuditLogItem[]>('/admin/audit-logs', {
      params: {
        fromDate: params.fromDate || undefined,
        toDate: params.toDate || undefined,
        actorId: params.actorId || undefined,
        action: params.action && params.action !== 'ALL' ? params.action : undefined,
        keyword: params.keyword?.trim() || undefined,
      },
    })
    return response.data
  },

  getById: async (id: number): Promise<AuditLogItem> => {
    const response = await apiClient.get<AuditLogItem>(`/admin/audit-logs/${id}`)
    return response.data
  },

  getFilterOptions: async (): Promise<AuditFilterOptions> => {
    const response = await apiClient.get<AuditFilterOptions>('/admin/audit-logs/filter-options')
    return response.data
  },
}
