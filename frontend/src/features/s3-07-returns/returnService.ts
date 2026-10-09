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

export interface ConfirmReturnResult {
  message: string
  copyId: number
  barcode: string
  bookTitle: string
  loanId: number
  loanNumber: string
  itemId: number
  itemStatus: 'RETURNED'
  loanStatus: 'BORROWED' | 'RETURNED'
  copyStatus: 'AVAILABLE' | 'HELD'
  returnedAt: string
  returnedById: number
  returnedByName: string
  nextReservationId: number | null
  nextReaderName: string | null
  holdStartedAt: string | null
  pickupDeadline: string | null
}

export interface ReturnSessionSuccess extends ConfirmReturnResult {
  status: 'SUCCESS'
  readerName: string | null
  dueAt: string | null
  overdueDays: number | null
}

export interface ReturnSessionFailure {
  status: 'ERROR'
  barcode: string
  bookTitle: string | null
  readerName: string | null
  dueAt: string | null
  message: string
}

export type ReturnSessionEntry = ReturnSessionSuccess | ReturnSessionFailure

// Use the server's actual return time, even if lookup and confirmation straddle midnight.
// Match LoanService: calendar days in Vietnam, including library closed dates.
export function returnOverdueDays(dueAt: string | null, returnedAt: string): number | null {
  function calendarDay(value: string | null): number | null {
    if (!value) return null
    const date = new Date(value)
    if (!Number.isFinite(date.getTime())) return null
    const parts = new Intl.DateTimeFormat('en-US', {
      timeZone: 'Asia/Ho_Chi_Minh', year: 'numeric', month: '2-digit', day: '2-digit',
    }).formatToParts(date)
    const get = (type: string) => Number(parts.find(part => part.type === type)?.value)
    return Date.UTC(get('year'), get('month') - 1, get('day'))
  }
  const due = calendarDay(dueAt), returned = calendarDay(returnedAt)
  return due === null || returned === null ? null : Math.max(0, Math.round((returned - due) / 86400000))
}

export function hasReceivedCopy(entries: ReturnSessionEntry[], barcode: string, copyId?: number): boolean {
  return entries.some(entry => entry.status === 'SUCCESS'
    && (entry.barcode === barcode.trim() || (copyId !== undefined && entry.copyId === copyId)))
}

export function recordReturnResult(entries: ReturnSessionEntry[], entry: ReturnSessionEntry): ReturnSessionEntry[] {
  if (hasReceivedCopy(entries, entry.barcode, entry.status === 'SUCCESS' ? entry.copyId : undefined)) return entries
  const index = entries.findIndex(previous => previous.barcode === entry.barcode)
  // Retrying a failed barcode updates its own row and preserves all other results.
  return index < 0 ? [...entries, entry] : entries.map((previous, i) => i === index ? entry : previous)
}

export function returnSessionSuccess(preview: ReturnLookup, result: ConfirmReturnResult): ReturnSessionSuccess {
  return { ...result, status: 'SUCCESS', readerName: preview.readerName, dueAt: preview.dueAt,
    overdueDays: returnOverdueDays(preview.dueAt, result.returnedAt) }
}

export function validateReturnBarcode(barcode: string): string {
  const value = barcode.trim()
  return !value || value.length > 100 ? 'Vui lòng nhập mã vạch từ 1 đến 100 ký tự.' : ''
}

export const returnService = {
  async confirm(barcode: string, itemId: number): Promise<ConfirmReturnResult> {
    const response = await apiClient.post<ConfirmReturnResult>('/loans/return-confirmation', {
      barcode: barcode.trim(), itemId,
    })
    return response.data
  },
  async lookup(barcode: string, signal?: AbortSignal): Promise<ReturnLookup> {
    const response = await apiClient.get<ReturnLookup>('/loans/return-lookup', {
      params: { barcode: barcode.trim() }, signal,
    })
    return response.data
  },
}
