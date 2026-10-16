import { useEffect, useRef, useState } from 'react'
import { FEEDBACK_DURATION_MS } from './FeedbackAlert'

interface Props { children?: string; id?: string; className?: string }

/** Hide the message, keeping the parent's validation state and submission guards intact. */
export default function TimedFieldMessage({ children: message = '', id, className = 'mt-1.5 text-xs font-medium text-red-600' }: Props) {
  const [hiddenMessage, setHiddenMessage] = useState<string | null>(null)
  const element = useRef<HTMLParagraphElement>(null)
  useEffect(() => {
    if (!message) { setHiddenMessage(message); return }
    let timer: number | undefined
    const restart = () => {
      window.clearTimeout(timer)
      setHiddenMessage(null)
      timer = window.setTimeout(() => setHiddenMessage(message), FEEDBACK_DURATION_MS)
    }
    restart()
    // Repeating an invalid submission must show the same message again for a full 3s.
    const form = element.current?.closest('form')
    form?.addEventListener('submit', restart)
    return () => { window.clearTimeout(timer); form?.removeEventListener('submit', restart) }
  }, [message])
  if (!message || hiddenMessage === message) return null
  return <p ref={element} id={id} role="alert" className={className}>{message}</p>
}
