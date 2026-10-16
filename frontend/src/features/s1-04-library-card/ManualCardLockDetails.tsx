import type { ManualCardLock } from './libraryCardService'

export function formatLockTime(value: string) {
  return new Date(value).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })
}

export default function ManualCardLockDetails({ lock }: { lock?: ManualCardLock | null }) {
  if (!lock?.active) return null
  return <div className="min-w-0 space-y-1 break-words text-sm">
    <p><strong>Lý do khóa:</strong> {lock.reason}</p>
    <p>Bắt đầu: {formatLockTime(lock.startedAt)}</p>
    <p>{lock.endsAt ? `Khóa đến: ${formatLockTime(lock.endsAt)} (giờ Việt Nam)` : 'Khóa đến khi mở thủ công'}</p>
  </div>
}
