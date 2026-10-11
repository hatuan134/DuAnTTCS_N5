import type {
  ButtonHTMLAttributes,
  ReactNode,
} from 'react'

type ButtonVariant =
  | 'primary'
  | 'secondary'
  | 'success'
  | 'warning'
  | 'danger'
  | 'info'
  | 'ghost'

type ButtonSize =
  | 'sm'
  | 'md'
  | 'lg'

interface ButtonProps
  extends ButtonHTMLAttributes<HTMLButtonElement> {
  children: ReactNode
  variant?: ButtonVariant
  size?: ButtonSize
  loading?: boolean
}

export default function Button({
  children,
  variant = 'primary',
  size = 'md',
  loading = false,
  disabled,
  className = '',
  ...props
}: ButtonProps) {
  const variants: Record<ButtonVariant, string> = {
    primary:
      'border border-blue-600 bg-blue-600 text-white shadow-sm shadow-blue-600/10 hover:border-blue-700 hover:bg-blue-700 focus:ring-blue-200',
    secondary:
      'border border-slate-300 bg-white text-slate-700 shadow-sm hover:border-slate-400 hover:bg-slate-50 focus:ring-slate-200',
    success:
      'border border-emerald-600 bg-emerald-600 text-white shadow-sm hover:border-emerald-700 hover:bg-emerald-700 focus:ring-emerald-200',
    warning:
      'border border-amber-700 bg-amber-700 text-white shadow-sm hover:border-amber-800 hover:bg-amber-800 focus:ring-amber-200',
    danger:
      'border border-red-600 bg-red-600 text-white shadow-sm hover:border-red-700 hover:bg-red-700 focus:ring-red-200',
    info:
      'border border-sky-600 bg-sky-600 text-white shadow-sm hover:border-sky-700 hover:bg-sky-700 focus:ring-sky-200',
    ghost:
      'border border-transparent bg-transparent text-slate-600 hover:bg-slate-100 hover:text-slate-900 focus:ring-slate-200',
  }

  const sizes: Record<ButtonSize, string> = {
    sm: 'min-h-9 px-3 text-sm',
    md: 'min-h-11 px-4 text-sm',
    lg: 'min-h-12 px-5 text-base',
  }

  return (
    <button
      aria-busy={loading || undefined}
      disabled={disabled || loading}
      className={[
        'inline-flex items-center justify-center gap-2 rounded-xl',
        'font-semibold transition-all duration-150',
        'outline-none focus:ring-4',
        'disabled:cursor-not-allowed disabled:opacity-50',
        variants[variant],
        sizes[size],
        className,
      ].join(' ')}
      {...props}
    >
      {loading && (
        <span
          className="h-4 w-4 animate-spin rounded-full border-2 border-current border-r-transparent"
          aria-hidden="true"
        />
      )}

      {children}
    </button>
  )
}
