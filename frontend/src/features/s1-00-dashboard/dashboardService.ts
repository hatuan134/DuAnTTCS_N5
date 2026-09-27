import { apiClient } from '../../core/api/apiClient'

export interface DashboardStatsResponse {
  totalAccounts: number
  activeReaders: number
  issuedLibraryCards: number
  pendingReaderRequests: number
}

export async function getDashboardStats(): Promise<DashboardStatsResponse> {
  const response = await apiClient.get<DashboardStatsResponse>(
    '/dashboard/stats',
  )

  return response.data
}
