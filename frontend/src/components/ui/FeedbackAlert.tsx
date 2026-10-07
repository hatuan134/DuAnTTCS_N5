import { useEffect, useRef } from 'react'
import { AlertCircle, CheckCircle2, X } from 'lucide-react'

type FeedbackTone = 'success' | 'error'

interface FeedbackAlertProps {
  message: string
  tone: FeedbackTone
  onDismiss: () => void
  className?: string
  durationMs?: number
}

export default function FeedbackAlert({
  message,
  tone,
  onDismiss,
  className = '',
  durationMs = 3000,
}: FeedbackAlertProps) {
  const dismissRef = useRef(onDismiss)

  useEffect(() => {
    dismissRef.current = onDismiss
  }, [onDismiss])

  useEffect(() => {
    if (!message) return

    const timer = window.setTimeout(() => {
      dismissRef.current()
    }, durationMs)

    return () => window.clearTimeout(timer)
  }, [durationMs, message])

  if (!message) return null

  const success = tone === 'success'

  return (
    <div
      role={success ? 'status' : 'alert'}
      aria-live={success ? 'polite' : 'assertive'}
      className={[
        'flex items-start justify-between gap-3 rounded-xl border px-4 py-3 text-sm shadow-sm',
        success
          ? 'border-emerald-200 bg-emerald-50 text-emerald-800'
          : 'border-red-200 bg-red-50 text-red-700',
        className,
      ].join(' ')}
    >
      <div className="flex min-w-0 items-start gap-2.5">
        {success ? (
          <CheckCircle2 className="mt-0.5 shrink-0 text-emerald-600" size={18} />
        ) : (
          <AlertCircle className="mt-0.5 shrink-0 text-red-600" size={18} />
        )}
        <span className="min-w-0 leading-6">{message}</span>
      </div>

      <button
        type="button"
        onClick={onDismiss}
        className={[
          'shrink-0 rounded-lg p-1 transition',
          success
            ? 'text-emerald-600 hover:bg-emerald-100 hover:text-emerald-800'
            : 'text-red-600 hover:bg-red-100 hover:text-red-800',
        ].join(' ')}
        aria-label="Đóng thông báo"
      >
        <X size={16} />
      </button>
    </div>
  )
}
