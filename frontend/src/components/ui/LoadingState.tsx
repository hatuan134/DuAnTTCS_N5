export default function LoadingState() {
  return (
    <div role="status" aria-live="polite" aria-busy="true" className="flex min-h-48 items-center justify-center rounded-2xl border border-slate-200/80 bg-white/70">
      <div className="flex items-center gap-3 text-sm font-medium text-slate-500">
        <span className="h-5 w-5 animate-spin rounded-full border-2 border-blue-200 border-t-blue-600" aria-hidden="true" />
        Đang tải dữ liệu...
      </div>
    </div>
  )
}
