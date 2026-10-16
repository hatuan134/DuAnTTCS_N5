import StatusBadge from '../../components/ui/StatusBadge'

interface BookCopyStatusBadgeProps {
  status: string
  label: string
}

export default function BookCopyStatusBadge({
  status,
  label,
}: BookCopyStatusBadgeProps) {
  return <StatusBadge status={status} label={label} />
}
