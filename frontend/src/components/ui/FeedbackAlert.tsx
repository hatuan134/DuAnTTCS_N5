import { useEffect, useRef } from 'react'
import { AlertCircle, CheckCircle2, X } from 'lucide-react'

type FeedbackTone = 'success' | 'error' | 'warning' | 'info'

export const FEEDBACK_DURATION_MS = 3000

interface FeedbackAlertProps {
  message: string
  tone: FeedbackTone
  onDismiss: () => void
  className?: string
}

export default function FeedbackAlert({
  message,
  tone,
  onDismiss,
  className = '',
}: FeedbackAlertProps) {
  const dismissRef = useRef(onDismiss)

  useEffect(() => {
    dismissRef.current = onDismiss
  }, [onDismiss])

  useEffect(() => {
    if (!message) return

    const timer = window.setTimeout(() => {
      dismissRef.current()
    }, FEEDBACK_DURATION_MS)

    return () => window.clearTimeout(timer)
  }, [message, tone])

  if (!message) return null

  const success = tone === 'success'
  const urgent = tone === 'error' || tone === 'warning'
  const styles = {
    success: 'border-emerald-200 bg-emerald-50 text-emerald-800',
    error: 'border-red-200 bg-red-50 text-red-700',
    warning: 'border-amber-200 bg-amber-50 text-amber-800',
    info: 'border-blue-200 bg-blue-50 text-blue-800',
  }
  const closeStyles = {
    success: 'text-emerald-600 hover:bg-emerald-100 hover:text-emerald-800',
    error: 'text-red-600 hover:bg-red-100 hover:text-red-800',
    warning: 'text-amber-700 hover:bg-amber-100 hover:text-amber-900',
    info: 'text-blue-700 hover:bg-blue-100 hover:text-blue-900',
  }

  return (
    <div
      role={urgent ? 'alert' : 'status'}
      aria-live={urgent ? 'assertive' : 'polite'}
      className={[
        'feedback-alert flex items-start justify-between gap-3 rounded-xl border px-4 py-3 text-sm shadow-sm',
        styles[tone],
        className,
      ].join(' ')}
    >
      <div className="flex min-w-0 items-start gap-2.5">
        {success ? (
          <CheckCircle2 className="mt-0.5 shrink-0 text-emerald-600" size={18} />
        ) : (
          <AlertCircle className="mt-0.5 shrink-0" size={18} />
        )}
        <span className="min-w-0 break-words leading-6">{message}</span>
      </div>

      <button
        type="button"
        onClick={onDismiss}
        className={[
          'shrink-0 rounded-lg p-2 transition',
          closeStyles[tone],
        ].join(' ')}
        aria-label="Đóng thông báo"
      >
        <X size={16} />
      </button>
    </div>
  )
}
