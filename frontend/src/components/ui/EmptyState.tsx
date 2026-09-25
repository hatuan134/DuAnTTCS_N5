interface EmptyStateProps {
  title?: string
  description?: string
}

export default function EmptyState({
  title = 'Chưa có dữ liệu',
  description = 'Không có dữ liệu để hiển thị.',
}: EmptyStateProps) {
  return (
    <div className="rounded-lg border border-dashed border-slate-300 bg-white px-6 py-12 text-center">
      <p className="font-medium text-slate-700">
        {title}
      </p>

      <p className="mt-1 text-sm text-slate-500">
        {description}
      </p>
    </div>
  )
}