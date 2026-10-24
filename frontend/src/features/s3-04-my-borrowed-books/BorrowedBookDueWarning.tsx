interface Props {
  remainingDays: number | null
}

/** The API keeps signed days for eligibility; presentation never shows negative numbers. */
export default function BorrowedBookDueWarning({ remainingDays }: Props) {
  if (remainingDays === null || !Number.isSafeInteger(remainingDays)) {
    return <span className="text-sm text-slate-600">Chưa xác định hạn trả</span>
  }
  if (remainingDays < 0) return <span className="inline-flex rounded-full bg-red-50 px-3 py-1 text-sm font-semibold text-red-700">
    Quá hạn {Math.abs(remainingDays)} ngày
  </span>
  if (remainingDays === 0) return <span className="inline-flex rounded-full bg-orange-50 px-3 py-1 text-sm font-semibold text-orange-800">Đến hạn hôm nay</span>
  if (remainingDays <= 2) return <span className="inline-flex rounded-full bg-amber-50 px-3 py-1 text-sm font-semibold text-amber-800">
    Sắp đến hạn · Còn {remainingDays} ngày
  </span>
  return <span className="inline-flex rounded-full bg-emerald-50 px-3 py-1 text-sm font-semibold text-emerald-800">
    Còn {remainingDays} ngày
  </span>
}
