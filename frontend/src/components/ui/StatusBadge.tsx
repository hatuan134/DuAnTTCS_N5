interface StatusBadgeProps {
  status: string
  label?: string
}

const styles: Record<string, string> = {
  ACTIVE: 'border-emerald-200 bg-emerald-50 text-emerald-700',
  AVAILABLE: 'border-emerald-200 bg-emerald-50 text-emerald-700',
  APPROVED: 'border-emerald-200 bg-emerald-50 text-emerald-700',
  FULFILLED: 'border-emerald-200 bg-emerald-50 text-emerald-700',
  RETURNED: 'border-emerald-200 bg-emerald-50 text-emerald-700',
  PENDING: 'border-amber-200 bg-amber-50 text-amber-700',
  READY_FOR_PICKUP: 'border-sky-200 bg-sky-50 text-sky-700',
  HELD: 'border-amber-200 bg-amber-50 text-amber-700',
  NO_COPY: 'border-amber-200 bg-amber-50 text-amber-700',
  BORROWED: 'border-blue-200 bg-blue-50 text-blue-700',
  REPAIR: 'border-orange-200 bg-orange-50 text-orange-700',
  DAMAGED: 'border-orange-200 bg-orange-50 text-orange-700',
  LOCKED: 'border-red-200 bg-red-50 text-red-700',
  REJECTED: 'border-red-200 bg-red-50 text-red-700',
  OVERDUE: 'border-red-200 bg-red-50 text-red-700',
  LOST: 'border-red-200 bg-red-50 text-red-700',
  DISABLED: 'border-slate-200 bg-slate-100 text-slate-600',
  CANCELLED: 'border-slate-200 bg-slate-100 text-slate-600',
  EXPIRED: 'border-slate-200 bg-slate-100 text-slate-600',
  REMOVED: 'border-slate-200 bg-slate-100 text-slate-600',
}

const labels: Record<string, string> = {
  ACTIVE: 'Đang hoạt động',
  AVAILABLE: 'Sẵn sàng',
  APPROVED: 'Đã duyệt',
  FULFILLED: 'Đã hoàn tất',
  RETURNED: 'Đã trả',
  PENDING: 'Chờ xử lý',
  READY_FOR_PICKUP: 'Chờ nhận',
  HELD: 'Đang giữ',
  NO_COPY: 'Chưa có bản sao',
  BORROWED: 'Đang mượn',
  REPAIR: 'Đang sửa chữa',
  DAMAGED: 'Hư hỏng',
  LOCKED: 'Bị khóa',
  REJECTED: 'Đã từ chối',
  OVERDUE: 'Quá hạn',
  LOST: 'Bị mất',
  DISABLED: 'Ngừng hoạt động',
  CANCELLED: 'Đã hủy',
  EXPIRED: 'Hết hạn',
  REMOVED: 'Đã loại',
}

export default function StatusBadge({
  status,
  label,
}: StatusBadgeProps) {
  return (
    <span
      className={[
        'inline-flex items-center rounded-full border px-2.5 py-1 text-xs font-semibold leading-none',
        styles[status] ?? 'border-slate-200 bg-slate-100 text-slate-600',
      ].join(' ')}
    >
      {label ?? labels[status] ?? status}
    </span>
  )
}
