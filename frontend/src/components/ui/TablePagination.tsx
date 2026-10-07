import { ChevronLeft, ChevronRight } from 'lucide-react'

interface TablePaginationProps {
  page: number
  totalItems: number
  totalPages: number
  onPageChange: (page: number) => void
  pageSize?: number
  className?: string
}

export default function TablePagination({
  page,
  totalItems,
  totalPages,
  onPageChange,
  pageSize = 10,
  className = '',
}: TablePaginationProps) {
  if (totalItems === 0) return null

  const start = (page - 1) * pageSize + 1
  const end = Math.min(page * pageSize, totalItems)

  return (
    <div
      className={`flex flex-col gap-3 border-t border-slate-200 bg-white px-5 py-4 text-sm text-slate-600 sm:flex-row sm:items-center sm:justify-between ${className}`}
      aria-label="Phân trang bảng"
    >
      <p>
        Hiển thị <strong className="text-slate-800">{start}–{end}</strong> trên{' '}
        <strong className="text-slate-800">{totalItems}</strong> bản ghi
      </p>

      <div className="flex items-center justify-center gap-2">
        <button
          type="button"
          onClick={() => onPageChange(page - 1)}
          disabled={page <= 1}
          className="inline-flex min-h-9 items-center gap-1 rounded-lg border border-slate-300 bg-white px-3 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-45"
          aria-label="Trang trước"
        >
          <ChevronLeft size={16} />
          Trước
        </button>

        <span className="min-w-24 text-center font-medium text-slate-700">
          Trang {page} / {totalPages}
        </span>

        <button
          type="button"
          onClick={() => onPageChange(page + 1)}
          disabled={page >= totalPages}
          className="inline-flex min-h-9 items-center gap-1 rounded-lg border border-slate-300 bg-white px-3 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-45"
          aria-label="Trang sau"
        >
          Sau
          <ChevronRight size={16} />
        </button>
      </div>
    </div>
  )
}
