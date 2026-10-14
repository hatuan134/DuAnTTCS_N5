import { apiClient } from '../../core/api/apiClient'
import type { LibraryCard } from '../s1-04-library-card/libraryCardService'
export interface SuggestionSettings {
  debtThresholdVnd: number; updatedAt: string; updatedBy: number | null; lastCheckedAt: string | null
}
export type SuggestionStatus = 'PENDING' | 'ACCEPTED' | 'IGNORED' | 'RESOLVED' | 'CANCELLED_LOCKED'
export interface CardLockSuggestion {
  id: number; cardId: number; cardNumber: string; readerName: string
  maxOverdueDays: number; unpaidFeesVnd: string; debtThresholdVnd: number
  reasonCode: 'OVERDUE' | 'DEBT' | 'BOTH'; status: SuggestionStatus; episodeOpen: boolean
  detectedAt: string; updatedAt: string; decidedBy: number | null; deciderName: string | null
  decidedAt: string | null; decisionReason: string | null; cardStatusAfterDecision: string | null
  lockEndsAt: string | null; resolvedAt: string | null; currentCardStatus: string
}
export interface SuggestionEvidence {
  overdueItems: { loanId: number; loanNumber: string; itemId: number; bookTitle: string; barcode: string; dueAt: string; overdueDays: number }[]
  fees: { id: number; description: string | null; originalVnd: string; paidVnd: string; waivedVnd: string; outstandingVnd: string }[]
}
export interface SuggestionPage {
  items: CardLockSuggestion[]; page: number; size: number; totalItems: number; totalPages: number; settings: SuggestionSettings
}
export interface SuggestionDetail {
  suggestion: CardLockSuggestion; card: LibraryCard; evidence: SuggestionEvidence; currentEvidence: SuggestionEvidence
  currentMaxOverdueDays: number; currentUnpaidFeesVnd: string; currentDebtThresholdVnd: number; currentReasonCode: string | null
}
export interface SuggestionDecision {
  suggestion: CardLockSuggestion; card: LibraryCard; accepted: boolean; message: string
}
const base = '/library-cards/lock-suggestions'
export const cardLockSuggestionService = {
  async settings(): Promise<SuggestionSettings> { return (await apiClient.get(`${base}/settings`)).data },
  async updateSettings(debtThresholdVnd: number): Promise<SuggestionSettings> { return (await apiClient.put(`${base}/settings`, { debtThresholdVnd })).data },
  async list(history: boolean, page = 0): Promise<SuggestionPage> { return (await apiClient.get(base, { params: { history, page, size: 20 } })).data },
  async detail(id: number): Promise<SuggestionDetail> { return (await apiClient.get(`${base}/${id}`)).data },
  async accept(id: number, reason: string, endsAt: string | null): Promise<SuggestionDecision> { return (await apiClient.post(`${base}/${id}/accept`, { reason, endsAt })).data },
  async ignore(id: number, reason: string): Promise<SuggestionDecision> { return (await apiClient.post(`${base}/${id}/ignore`, { reason })).data },
}
export function parseDebtThreshold(value: string): number | null {
  const cleaned = value.trim()
  if (!/^\d{1,13}$/.test(cleaned)) return null
  const parsed = Number(cleaned)
  return Number.isSafeInteger(parsed) && parsed <= 1_000_000_000_000 ? parsed : null
}
export function suggestionMoney(value: string | number) {
  return `${BigInt(value).toLocaleString('vi-VN')} ₫`
}
export function suggestionReason(code: string | null) {
  return code === 'BOTH' ? 'Quá hạn trên 30 ngày mở cửa và nợ phí vượt ngưỡng'
    : code === 'OVERDUE' ? 'Quá hạn trên 30 ngày mở cửa'
    : code === 'DEBT' ? 'Nợ phí vượt ngưỡng' : 'Không còn vi phạm'
}
export const suggestionStatus: Record<SuggestionStatus, string> = {
  PENDING: 'Chờ xử lý', ACCEPTED: 'Đã chấp nhận khóa', IGNORED: 'Đã bỏ qua',
  RESOLVED: 'Đã hết vi phạm', CANCELLED_LOCKED: 'Kết thúc vì thẻ đã khóa',
}
