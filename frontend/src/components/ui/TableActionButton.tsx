import type { ButtonHTMLAttributes, ReactNode } from 'react'

type TableActionTone = 'primary' | 'neutral' | 'success' | 'warning' | 'danger'

const toneClasses: Record<TableActionTone, string> = {
  primary: 'border-blue-200 bg-blue-50 text-blue-700 hover:border-blue-300 hover:bg-blue-100',
  neutral: 'border-slate-300 bg-white text-slate-700 hover:border-slate-400 hover:bg-slate-50',
  success: 'border-emerald-200 bg-emerald-50 text-emerald-700 hover:border-emerald-300 hover:bg-emerald-100',
  warning: 'border-amber-200 bg-amber-50 text-amber-700 hover:border-amber-300 hover:bg-amber-100',
  danger: 'border-red-200 bg-red-50 text-red-700 hover:border-red-300 hover:bg-red-100',
}

export function tableActionClassName(tone: TableActionTone = 'neutral', className = '') {
  return [
    'inline-flex min-h-8 items-center justify-center gap-1.5 whitespace-nowrap rounded-lg border px-2.5 text-xs font-semibold shadow-sm transition',
    'focus:outline-none focus:ring-4 focus:ring-blue-100',
    'disabled:cursor-not-allowed disabled:opacity-45',
    toneClasses[tone],
    className,
  ].join(' ')
}

interface TableActionButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  children: ReactNode
  icon?: ReactNode
  tone?: TableActionTone
}

function actionLabel(children: ReactNode, title?: string) {
  if (title) return title
  if (typeof children === 'string' || typeof children === 'number') return String(children)
  return undefined
}

export default function TableActionButton({
  children,
  icon,
  tone = 'neutral',
  className = '',
  title,
  ...props
}: TableActionButtonProps) {
  const tooltip = actionLabel(children, title)

  return (
    <span className="inline-flex shrink-0" title={tooltip}>
      <button
        type="button"
        className={tableActionClassName(tone, className)}
        aria-label={props['aria-label'] ?? tooltip}
        title={tooltip}
        {...props}
      >
        {icon && (
          <span className="inline-flex h-4 w-4 shrink-0 items-center justify-center" aria-hidden="true">
            {icon}
          </span>
        )}
        <span className="leading-none">{children}</span>
      </button>
    </span>
  )
}

export function TableActions({ children }: { children: ReactNode }) {
  return (
    <div className="table-actions flex flex-nowrap items-center justify-center gap-1.5 whitespace-nowrap">
      {children}
    </div>
  )
}
