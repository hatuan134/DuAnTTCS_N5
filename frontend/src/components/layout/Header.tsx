export default function Header() {
  return (
    <header className="flex h-16 items-center justify-between border-b border-slate-200 bg-white px-6">
      <div>
        <h1 className="text-lg font-semibold text-slate-900">
          Hệ thống Quản lý Thư viện
        </h1>

        <p className="text-xs text-slate-500">
          Quản lý mượn / trả sách
        </p>
      </div>

      <div className="text-right">
        <div className="text-sm font-medium text-slate-900">
          Quản trị hệ thống
        </div>

        <div className="text-xs text-slate-500">
          ADMIN
        </div>
      </div>
    </header>
  )
}