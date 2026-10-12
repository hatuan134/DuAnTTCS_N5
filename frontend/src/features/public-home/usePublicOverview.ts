import { useEffect, useState } from 'react'
import { apiClient } from '../../core/api/apiClient'

export type OpeningDay = { dayOfWeek: number; open: boolean; openTime: string | null; closeTime: string | null }
export type PublicOverview = { totalTitles: number; totalCopies: number; totalReaders: number; totalLoans: number; weeklySchedule: OpeningDay[] }

export function usePublicOverview() {
  const [overview, setOverview] = useState<PublicOverview | null>(null)
  const [error, setError] = useState(false)
  useEffect(() => {
    const controller = new AbortController()
    apiClient.get<PublicOverview>('/public/overview', { signal: controller.signal })
      .then(res => { if (!controller.signal.aborted) setOverview(res.data) })
      .catch(() => { if (!controller.signal.aborted) setError(true) })
    return () => controller.abort()
  }, [])
  return { overview, error }
}
