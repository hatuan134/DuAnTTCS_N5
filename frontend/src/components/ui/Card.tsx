import type { ReactNode } from 'react'

interface CardProps {
  children: ReactNode
  className?: string
}

export default function Card({
  children,
  className = '',
}: CardProps) {
  return (
    <div
      className={[
        'rounded-2xl border border-slate-200/90 bg-white',
        'shadow-[0_1px_2px_rgba(15,23,42,0.03),0_8px_24px_rgba(15,23,42,0.035)]',
        className,
      ].join(' ')}
    >
      {children}
    </div>
  )
}
