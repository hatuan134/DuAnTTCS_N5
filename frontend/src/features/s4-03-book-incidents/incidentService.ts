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
  photos: { id: number; filename: string; contentType: string }[]
}
export const incidentTypeLabel = (type: IncidentType) => type === 'LOST' ? 'Mất' : 'Hỏng nặng'
export function closureLabel(reason?: string | null): string {
  return reason === 'LOST' ? 'Mất' : reason === 'DAMAGED' ? 'Hỏng' : reason === 'MIXED_INCIDENT' ? 'Mất và Hỏng' : 'Đã trả'
}
export const incidentService = {
  async factors(): Promise<Factors> { return (await apiClient.get<Factors>('/book-incidents/factors')).data },
  async updateFactors(value: Factors): Promise<Factors> { return (await apiClient.put<Factors>('/book-incidents/factors', value)).data },
  async search(query: string): Promise<IncidentItem[]> { return (await apiClient.get<IncidentItem[]>('/book-incidents/open-items', { params: { query } })).data },
  async preview(itemId: number, type: IncidentType): Promise<IncidentPreview> {
    return (await apiClient.get<IncidentPreview>('/book-incidents/preview', { params: { itemId, type } })).data
  },
  async confirm(preview: IncidentPreview, description: string, files: File[]): Promise<IncidentRecord> {
    const form = new FormData()
    form.append('itemId', String(preview.item.itemId)); form.append('type', preview.type)
    form.append('description', description.trim()); form.append('previewToken', preview.token)
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
