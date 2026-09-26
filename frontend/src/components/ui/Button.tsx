import type {
  ButtonHTMLAttributes,
  ReactNode,
} from 'react'

type ButtonVariant =
  | 'primary'
  | 'secondary'
  | 'danger'

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
      'bg-blue-600 text-white hover:bg-blue-700 focus:ring-blue-200',
    secondary:
      'border border-slate-300 bg-white text-slate-700 hover:bg-slate-50 focus:ring-slate-200',
    danger:
      'bg-red-600 text-white hover:bg-red-700 focus:ring-red-200',
  }

  const sizes: Record<ButtonSize, string> = {
    sm: 'h-9 px-3 text-sm',
    md: 'h-11 px-4 text-sm',
    lg: 'h-12 px-5 text-base',
  }

  return (
    <button
      disabled={disabled || loading}
      className={[
        'inline-flex items-center justify-center gap-2 rounded-lg',
        'font-medium transition-all duration-150',
        'outline-none focus:ring-4',
        'disabled:cursor-not-allowed disabled:opacity-60',
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