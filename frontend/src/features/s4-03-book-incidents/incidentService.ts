import { apiClient } from '../../core/api/apiClient'
import type { ReturnFeeAssessment } from '../s3-07-returns/returnService'

export type IncidentType = 'LOST' | 'DAMAGED'
export interface Factors { lost: number | null; damaged: number | null; updatedAt?: string | null }
export interface IncidentItem {
  itemId: number; loanId: number; loanNumber: string; readerId: number; readerName: string
  copyId: number; barcode: string; bookTitle: string; dueAt: string | null; copyStatus: string
  closedAt: string | null; resolutionReason: string | null; coverPrice: number | null
}
export interface IncidentPreview {
  item: IncidentItem; type: IncidentType; factor: number; compensationVnd: number
  overdueFee: ReturnFeeAssessment; token: string
}
export interface IncidentRecord {
  id: number; item: IncidentItem; type: IncidentType; description: string; coverPrice: number
  factor: number; compensationVnd: number; compensationFeeId: number; overdueFee: ReturnFeeAssessment
  actorName: string; recordedAt: string; beforeStatus: string; afterStatus: string; loanStatus: string
  automaticCompensationVnd: number; adjustmentReason: string | null; actorUserId: number
  photos: { id: number; filename: string; contentType: string }[]
}
export const incidentTypeLabel = (type: IncidentType) => type === 'LOST' ? 'Mất' : 'Hỏng nặng'
export function closureLabel(reason?: string | null): string {
  return reason === 'LOST' ? 'Mất' : reason === 'DAMAGED' ? 'Hỏng' : reason === 'MIXED_INCIDENT' ? 'Mất và Hỏng' : 'Đã trả'
}
export interface IncidentAdjustment {
  id: number; number: string; incidentId: number; reason: string; caseType: string; targetStatus: string
  status: 'PENDING' | 'CONFIRMED'; requesterName: string; requestedAt: string
  confirmerName: string | null; confirmedAt: string | null; reversedVnd: number | null
  cancelledDebtVnd: number | null; refundDueVnd: number | null; beforeData: string | null; afterData: string | null
}
export interface AdjustmentPreview {
  reversedVnd: number; cancelledDebtVnd: number; refundDueVnd: number; copyStatus: string; loanStatus: string
}
export const adjustmentStatusLabel = (status: string) => ({ BORROWED: 'Đang mượn', AVAILABLE: 'Sẵn sàng', REPAIR: 'Sửa chữa', HELD: 'Đang giữ cho người đặt trước', REMOVED: 'Đã loại khỏi kho' }[status] ?? status)
export function validateReversalReason(reason: string): string {
  return !reason.trim() || reason.trim().length > 1900 ? 'Nhập lý do điều chỉnh từ 1 đến 1.900 ký tự.' : ''
}
export const incidentService = {
  async detail(id: number): Promise<IncidentRecord> { return (await apiClient.get<IncidentRecord>(`/book-incidents/${id}`)).data },
  async adjustments(id: number): Promise<IncidentAdjustment[]> { return (await apiClient.get<IncidentAdjustment[]>(`/book-incidents/${id}/adjustments`)).data },
  async adjustmentPreview(id: number, targetStatus: string): Promise<AdjustmentPreview> {
    return (await apiClient.get<AdjustmentPreview>(`/book-incidents/${id}/adjustment-preview`, { params: { targetStatus } })).data
  },
  async requestAdjustment(id: number, reason: string, caseType: string, targetStatus: string): Promise<IncidentAdjustment> {
    return (await apiClient.post<IncidentAdjustment>(`/book-incidents/${id}/adjustments`, { reason: reason.trim(), caseType, targetStatus })).data
  },
  async confirmAdjustment(id: number, adjustmentId: number): Promise<IncidentAdjustment> {
    return (await apiClient.post<IncidentAdjustment>(`/book-incidents/${id}/adjustments/${adjustmentId}/confirm`)).data
  },
  async factors(): Promise<Factors> { return (await apiClient.get<Factors>('/book-incidents/factors')).data },
  async updateFactors(value: Factors): Promise<Factors> { return (await apiClient.put<Factors>('/book-incidents/factors', value)).data },
  async search(query: string): Promise<IncidentItem[]> { return (await apiClient.get<IncidentItem[]>('/book-incidents/open-items', { params: { query } })).data },
  async preview(itemId: number, type: IncidentType): Promise<IncidentPreview> {
    return (await apiClient.get<IncidentPreview>('/book-incidents/preview', { params: { itemId, type } })).data
  },
  async confirm(preview: IncidentPreview, description: string, files: File[], adjustment?: { amount: string; reason: string }): Promise<IncidentRecord> {
    const form = new FormData()
    form.append('itemId', String(preview.item.itemId)); form.append('type', preview.type)
    form.append('description', description.trim()); form.append('previewToken', preview.token)
    if (adjustment) { form.append('proposedCompensationVnd', adjustment.amount.trim()); form.append('adjustmentReason', adjustment.reason.trim()) }
    for (const file of files) form.append('files', file)
    return (await apiClient.post<IncidentRecord>('/book-incidents', form, { headers: { 'Content-Type': undefined } })).data
  },
  async history(query: string): Promise<IncidentRecord[]> { return (await apiClient.get<IncidentRecord[]>('/book-incidents', { params: { query } })).data },
  async image(id: number, imageId: number): Promise<Blob> {
    return (await apiClient.get<Blob>(`/book-incidents/${id}/images/${imageId}`, { responseType: 'blob' })).data
  },
}
export function validateIncidentFiles(files: File[]): string {
  if (files.length > 5) return 'Chỉ được đính kèm tối đa 5 ảnh.'
  for (const file of files) {
    if (!/\.(jpe?g|png|webp)$/i.test(file.name) || !['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) return 'Chỉ nhận ảnh JPEG, PNG hoặc WebP.'
    if (file.size < 1 || file.size > 5 * 1024 * 1024) return 'Mỗi ảnh phải có dữ liệu và không vượt 5 MB.'
    if (file.name.length > 200) return 'Tên ảnh không vượt 200 ký tự.'
  }
  return ''
}
export function parseFactor(value: string): number | null {
  const normalized = value.trim().replace(',', '.')
  if (!/^\d+(?:\.\d{1,2})?$/.test(normalized)) return null
  const result = Number(normalized)
  return result > 0 && result <= 9999999999.99 ? result : null
}

export const MAX_ADJUSTED_VND = 10_000_000
export function validateCompensationAdjustment(automatic: number, amount: string, reason: string): string {
  const value = amount.trim()
  if (!/^[0-9]{1,16}$/.test(value) || !Number.isSafeInteger(Number(value))) return 'Nhập số nguyên VND không âm, không dùng dấu phân cách.'
  if (Number(value) === automatic) return ''
  if (Number(value) > MAX_ADJUSTED_VND) return 'Số tiền sau điều chỉnh không được vượt 10.000.000 đồng.'
  if (!reason.trim() || reason.trim().length > 1900) return 'Khi đổi tiền, phải nhập lý do từ 1 đến 1.900 ký tự.'
  return ''
}
