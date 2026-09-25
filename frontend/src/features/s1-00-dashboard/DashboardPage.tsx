const stats = [
  {
    title: 'Tài khoản',
    value: '248',
    description: 'Tổng số tài khoản',
  },
  {
    title: 'Bạn đọc',
    value: '196',
    description: 'Bạn đọc đang hoạt động',
  },
  {
    title: 'Thẻ thư viện',
    value: '182',
    description: 'Thẻ đã được cấp',
  },
  {
    title: 'Chờ xử lý',
    value: '12',
    description: 'Yêu cầu cần xử lý',
  },
]

export default function DashboardPage() {
  return (
    <div>
      <div>
        <h2 className="text-2xl font-semibold text-slate-900">
          Tổng quan
        </h2>

        <p className="mt-1 text-sm text-slate-500">
          Theo dõi tình trạng hệ thống thư viện.
        </p>
      </div>

      <div className="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {stats.map((item) => (
          <div
            key={item.title}
            className="rounded-lg border border-slate-200 bg-white p-5"
          >
            <div className="text-sm font-medium text-slate-600">
              {item.title}
            </div>

            <div className="mt-2 text-3xl font-semibold text-slate-900">
              {item.value}
            </div>

            <div className="mt-1 text-xs text-slate-400">
              {item.description}
            </div>
          </div>
        ))}
      </div>

      <div className="mt-6 rounded-lg border border-slate-200 bg-white p-6">
        <h3 className="font-semibold text-slate-900">
          Sprint 1
        </h3>

        <p className="mt-2 text-sm text-slate-600">
          Nền tảng tài khoản, thẻ thư viện và chính sách mượn.
        </p>
      </div>
    </div>
  )
}