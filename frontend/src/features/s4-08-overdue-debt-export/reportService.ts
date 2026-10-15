import axios from 'axios'
import { apiClient } from '../../core/api/apiClient'

export interface ReaderDebt {
  readerId: number; cardNumber: string | null; fullName: string; phone: string | null
  overdueBooks: number; maxOverdueDays: number; outstandingVnd: number
}
export interface Snapshot { snapshotAt: string; defaultFilter: string; readers: ReaderDebt[]; totalReaders: number }
export interface ExportLog {
  id: number; actorName: string; requestedAt: string; snapshotAt: string | null; completedAt: string | null
  defaultFilter: string; recordCount: number; status: 'PROCESSING' | 'SUCCESS' | 'FAILED'
}
export interface ExportHistory { items: ExportLog[]; page: number; size: number; totalItems: number }
const base = '/reports/overdue-debts'
export async function getPreview(signal?: AbortSignal) {
  return (await apiClient.get<Snapshot>(base, { signal })).data
}
export async function getHistory(page: number, signal?: AbortSignal) {
  return (await apiClient.get<ExportHistory>(`${base}/history`, { params: { page, size: 20 }, signal })).data
}
export async function exportExcel() {
  try {
    const response = await apiClient.post<Blob>(`${base}/export`, null, { responseType: 'blob' })
    const disposition = String(response.headers['content-disposition'] ?? '')
    const filename = disposition.match(/filename="?(DanhSachQuaHan_NoPhi_\d{2}-\d{2}-\d{4}\.xlsx)"?/)?.[1]
    if (!filename || !response.data.size) throw new Error('Không nhận được tệp Excel hợp lệ.')
    return { filename, content: response.data }
  } catch (error) {
    // Axios blob responses include error JSON as a Blob too.
    if (axios.isAxiosError(error) && error.response?.data instanceof Blob) {
      try { error.response.data = JSON.parse(await error.response.data.text()) } catch { /* retain original failure */ }
    }
    throw error
  }
}
export function saveDownload(filename: string, content: Blob) {
  const url = URL.createObjectURL(content)
  const anchor = document.createElement('a')
  anchor.href = url; anchor.download = filename
  try { document.body.appendChild(anchor); anchor.click() }
  finally { anchor.remove(); window.setTimeout(() => URL.revokeObjectURL(url), 1000) }
}
export const formatTime = (value: string) => new Intl.DateTimeFormat('vi-VN', {
  timeZone: 'Asia/Ho_Chi_Minh', dateStyle: 'short', timeStyle: 'medium', hour12: false,
}).format(new Date(value))
export const formatMoney = (value: number) => `${value.toLocaleString('vi-VN')} VND`
