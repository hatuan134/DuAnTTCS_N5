import type {
  InputHTMLAttributes,
  ReactNode,
} from 'react'

interface InputProps
  extends InputHTMLAttributes<HTMLInputElement> {
  label?: string
  error?: string
  startIcon?: ReactNode
  endAdornment?: ReactNode
}

export default function Input({
  label,
  error,
  startIcon,
  endAdornment,
  className = '',
  id,
  required,
  ...props
}: InputProps) {
  const inputId = id ?? props.name ?? props.type ?? 'input'

  return (
    <div className="w-full">
      {label && (
        <label
          htmlFor={inputId}
          className="mb-2 block text-sm font-semibold text-slate-700"
        >
          {label}
          {required && <span className="ml-1 text-red-500" aria-hidden="true">*</span>}
        </label>
      )}

      <div className="relative">
        {startIcon && (
          <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-slate-400">
            {startIcon}
          </div>
        )}

        <input
          id={inputId}
          required={required}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${inputId}-error` : undefined}
          className={[
            'min-h-11 w-full rounded-xl border bg-white',
            'text-sm text-slate-900 shadow-sm outline-none',
            'placeholder:text-slate-400',
            'transition-all duration-150',
            'focus:ring-4',
            'disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-500',
            startIcon ? 'pl-10' : 'pl-3.5',
            endAdornment ? 'pr-11' : 'pr-3.5',
            error
              ? 'border-red-400 focus:border-red-500 focus:ring-red-100'
              : 'border-slate-300 focus:border-blue-500 focus:ring-blue-100',
            className,
          ].join(' ')}
          {...props}
        />

        {endAdornment && (
          <div className="absolute inset-y-0 right-0 flex items-center pr-3">
            {endAdornment}
          </div>
        )}
      </div>

      {error && (
        <p id={`${inputId}-error`} className="mt-1.5 text-xs font-medium text-red-600">
          {error}
        </p>
      )}
    </div>
  )
}
