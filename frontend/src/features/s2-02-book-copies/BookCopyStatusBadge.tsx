interface BookCopyStatusBadgeProps {
  status: string
  label: string
}

const statusStyles: Record<string, string> = {
  AVAILABLE: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  BORROWED: 'bg-blue-50 text-blue-700 ring-blue-200',
  HELD: 'bg-amber-50 text-amber-700 ring-amber-200',
  REPAIR: 'bg-orange-50 text-orange-700 ring-orange-200',
  REMOVED: 'bg-slate-100 text-slate-700 ring-slate-200',
  LOST: 'bg-red-50 text-red-700 ring-red-200',
  DAMAGED: 'bg-rose-50 text-rose-700 ring-rose-200',
}

export default function BookCopyStatusBadge({ status, label }: BookCopyStatusBadgeProps) {
  return (
    <span
      className={[
        'inline-flex whitespace-nowrap rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ring-inset',
        statusStyles[status] ?? 'bg-slate-100 text-slate-700 ring-slate-200',
      ].join(' ')}
    >
      {label}
    </span>
  )
}
