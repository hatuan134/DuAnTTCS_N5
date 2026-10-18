export const text = (value: unknown) => typeof value === 'string' || typeof value === 'number' ? String(value) : ''
export const date = (value: unknown) => {
  if (!value) return 'Chưa có'
  const parsed = new Date(text(value))
  return Number.isNaN(parsed.getTime()) ? 'Chưa có' : parsed.toLocaleDateString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })
}
const labels: Record<string, string> = { ACTIVE: 'Đang hoạt động', EXPIRED: 'Hết hạn', LOCKED: 'Đã khóa', PENDING: 'Chờ duyệt', APPROVED: 'Đã duyệt', REJECTED: 'Bị từ chối', WAITING: 'Đang chờ', READY_FOR_PICKUP: 'Chờ nhận sách', FULFILLED: 'Đã nhận sách', CANCELLED: 'Đã hủy', BORROWED: 'Đang mượn', RETURNED: 'Đã trả', PARTIALLY_RETURNED: 'Đã trả một phần', EMPTY: 'Chưa có sách', LOST: 'Mất sách', DAMAGED: 'Sách hỏng', MIXED_INCIDENT: 'Có sự cố' }
export const status = (value: unknown) => labels[text(value)] ?? (text(value) || 'Chưa cấp thẻ')
