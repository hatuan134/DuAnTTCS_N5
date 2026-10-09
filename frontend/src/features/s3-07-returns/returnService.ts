import { apiClient } from '../../core/api/apiClient'

export interface ReturnLookup {
  status: 'NOT_BORROWED' | 'MISSING_DUE_DATE' | 'ON_TIME' | 'OVERDUE'
  message: string
  copyId: number
  barcode: string
  bookTitle: string
  loanId: number | null
  loanNumber: string | null
  itemId: number | null
  readerId: number | null
  readerName: string | null
  borrowedAt: string | null
  dueAt: string | null
  checkedOn: string
  overdueDays: number | null
}

export function validateReturnBarcode(barcode: string): string {
  const value = barcode.trim()
  return !value || value.length > 100 ? 'Vui lòng nhập mã vạch từ 1 đến 100 ký tự.' : ''
}

export const returnService = {
  async lookup(barcode: string, signal?: AbortSignal): Promise<ReturnLookup> {
    const response = await apiClient.get<ReturnLookup>('/loans/return-lookup', {
      params: { barcode: barcode.trim() }, signal,
    })
    return response.data
  },
}
