import StatusBadge from '../../components/ui/StatusBadge'

interface Props {
  remainingDays: number | null
}

/** Uses the server's Vietnam-calendar difference; these labels are persistent row content. */
export default function BorrowedBookDueWarning({ remainingDays }: Props) {
  if (remainingDays === null || !Number.isSafeInteger(remainingDays) || remainingDays >= 3) return null

  if (remainingDays < 0) return <span className="inline-flex flex-wrap items-center gap-2">
    <StatusBadge status="OVERDUE" />
    <span className="text-xs font-medium text-red-700">Trễ {-remainingDays} ngày</span>
  </span>

  return <StatusBadge status="PENDING" label="Sắp đến hạn" />
}
