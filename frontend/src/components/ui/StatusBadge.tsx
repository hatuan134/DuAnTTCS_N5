interface StatusBadgeProps {
  status: string
}

export default function StatusBadge({
  status,
}: StatusBadgeProps) {
  const styles: Record<string, string> = {
    ACTIVE:
      'bg-green-50 text-green-700',
    PENDING:
      'bg-amber-50 text-amber-700',
    LOCKED:
      'bg-red-50 text-red-700',
    DISABLED:
      'bg-slate-100 text-slate-600',
    APPROVED:
      'bg-green-50 text-green-700',
    REJECTED:
      'bg-red-50 text-red-700',
  }

  const labels: Record<string, string> = {
    ACTIVE: 'Đang hoạt động',
    PENDING: 'Chờ xử lý',
    LOCKED: 'Đã khóa',
    DISABLED: 'Ngừng hoạt động',
    APPROVED: 'Đã duyệt',
    REJECTED: 'Đã từ chối',
    EXPIRED: 'Hết hạn',
  }

  return (
    <span
      className={[
        'inline-flex rounded-full px-2.5 py-1 text-xs font-medium',
        styles[status] ??
          'bg-slate-100 text-slate-600',
      ].join(' ')}
    >
      {labels[status] ?? status}
    </span>
  )
}