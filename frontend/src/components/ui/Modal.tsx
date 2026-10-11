import { useEffect, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

let openModals = 0
let previousOverflow = ''

/** Native modal supplies an inert background; Tab wraps within visible controls. */
export default function Modal({ children, onClose, busy = false, label, labelledBy, describedBy, alert = false }:
  { children: ReactNode; onClose: () => void; busy?: boolean; label?: string; labelledBy?: string; describedBy?: string; alert?: boolean }) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const dialog = ref.current
    const previous = document.activeElement as HTMLElement | null
    if (openModals === 0) previousOverflow = document.body.style.overflow
    openModals += 1
    dialog?.showModal()
    document.body.style.overflow = 'hidden'
    return () => {
      dialog?.close()
      openModals -= 1
      if (openModals === 0) document.body.style.overflow = previousOverflow
      if (previous?.isConnected) previous.focus({ preventScroll: true })
    }
  }, [])
  return createPortal(
    <dialog ref={ref} tabIndex={-1} className="libra-modal" role={alert ? 'alertdialog' : 'dialog'} aria-modal="true"
      aria-label={label} aria-labelledby={labelledBy} aria-describedby={describedBy} aria-busy={busy}
      onKeyDown={(event) => {
        if (event.key !== 'Tab') return
        const dialog = event.currentTarget
        const controls = Array.from(dialog.querySelectorAll<HTMLElement>(
          'a[href], button, input, select, textarea, [tabindex]'))
          .filter(element => element.tabIndex >= 0 && !element.matches(':disabled')
            && !element.closest('[inert], [hidden]') && element.getClientRects().length > 0)
        const first = controls[0]
        const last = controls[controls.length - 1]
        if (!first) {
          event.preventDefault()
          dialog.focus()
        } else if (!controls.includes(document.activeElement as HTMLElement)
          || (event.shiftKey ? document.activeElement === first : document.activeElement === last)) {
          event.preventDefault()
          ;(event.shiftKey ? last : first).focus()
        }
      }}
      onCancel={(event) => { event.preventDefault(); if (!busy) onClose() }}
      onClick={(event) => { if (event.target === event.currentTarget && !busy) onClose() }}>
      {children}
    </dialog>, document.body)
}
