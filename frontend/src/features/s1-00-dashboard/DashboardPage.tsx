import {
  useEffect,
  useState,
} from 'react'

import {
  getDashboardStats,
} from './dashboardService'

import type {
  DashboardStatsResponse,
} from './dashboardService'

type DashboardState =
  | { status: 'loading'; data: null }
  | { status: 'success'; data: DashboardStatsResponse }
  | { status: 'error'; data: null }

export default function DashboardPage() {
  const [state, setState] =
    useState<DashboardState>({
      status: 'loading',
      data: null,
    })

  const loadStats = async () => {
    setState({
      status: 'loading',
      data: null,
    })

    try {
      const data = await getDashboardStats()
      setState({
        status: 'success',
        data,
      })
    } catch {
      setState({
        status: 'error',
        data: null,
      })
    }
  }

  useEffect(() => {
    void loadStats()
  }, [])

  const stats = [
    {
      title: 'Tài khoản',
      value: state.data?.totalAccounts,
      description: 'Tổng số tài khoản trong hệ thống',
    },
    {
      title: 'Bạn đọc',
      value: state.data?.activeReaders,
      description: 'Tài khoản bạn đọc đang hoạt động',
    },
    {
      title: 'Thẻ thư viện',
      value: state.data?.issuedLibraryCards,
      description: 'Tổng số thẻ đã được cấp',
    },
    {
      title: 'Chờ xử lý',
      value: state.data?.pendingReaderRequests,
      description: 'Hồ sơ bạn đọc đang chờ duyệt',
    },
  ]

  return (
    <div>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h2 className="text-2xl font-semibold text-slate-900">
            Tổng quan
          </h2>

          <p className="mt-1 text-sm text-slate-500">
            Theo dõi tình trạng hệ thống thư viện bằng dữ liệu thực tế.
          </p>
        </div>

        <button
          type="button"
          onClick={() => void loadStats()}
          disabled={state.status === 'loading'}
          className="rounded-md border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60"
        >
          {state.status === 'loading'
            ? 'Đang tải...'
            : 'Làm mới'}
        </button>
      </div>

      {state.status === 'error' && (
        <div className="mt-5 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
          Không thể tải số liệu tổng quan từ hệ thống. Vui lòng thử lại.
        </div>
      )}

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
              {state.status === 'loading'
                ? '...'
                : state.status === 'success'
                  ? item.value ?? 0
                  : '—'}
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
