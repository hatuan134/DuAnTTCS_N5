import { Inbox } from 'lucide-react'

interface EmptyStateProps {
  title?: string
  description?: string
}

export default function EmptyState({
  title = 'Chưa có dữ liệu',
  description = 'Không có dữ liệu để hiển thị.',
}: EmptyStateProps) {
  return (
    <div className="rounded-2xl border border-dashed border-slate-300 bg-white/80 px-6 py-12 text-center shadow-sm">
      <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-slate-100 text-slate-400">
        <Inbox size={22} />
      </div>
      <p className="mt-4 font-semibold text-slate-800">
        {title}
      </p>

      <p className="mx-auto mt-1.5 max-w-lg text-sm leading-6 text-slate-500">
        {description}
      </p>
    </div>
  )
}
