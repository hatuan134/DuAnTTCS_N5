interface Props {
  value: string
  onChange: (value: string) => void
  onCreate: () => void
  placeholder: string
  buttonLabel: string
  busy?: boolean
  disabled?: boolean
  maxLength?: number
}

export default function InlineCatalogCreateRow({
  value,
  onChange,
  onCreate,
  placeholder,
  buttonLabel,
  busy = false,
  disabled = false,
  maxLength = 255,
}: Props) {
  const blocked = disabled || busy || !value.trim()

  return (
    <div className="mt-2 flex flex-col gap-2 sm:flex-row">
      <input
        type="text"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            event.preventDefault()
            if (!blocked) onCreate()
          }
        }}
        maxLength={maxLength}
        placeholder={placeholder}
        disabled={disabled || busy}
        className="min-w-0 flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm outline-none focus:border-blue-500 disabled:cursor-not-allowed disabled:bg-slate-50"
      />
      <button
        type="button"
        onClick={onCreate}
        disabled={blocked}
        className="rounded-lg border border-blue-200 bg-blue-50 px-3 py-2 text-sm font-medium text-blue-700 hover:bg-blue-100 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {busy ? 'Đang thêm...' : buttonLabel}
      </button>
    </div>
  )
}
