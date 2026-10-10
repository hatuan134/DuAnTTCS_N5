import {
  useEffect,
  useState,
} from 'react'
import {
  CreditCard,
  RefreshCw,
  UserRoundCheck,
  UsersRound,
  UserRoundSearch,
} from 'lucide-react'

import Button from '../../components/ui/Button'
import { getCurrentUser } from '../../core/auth/authStorage'
import Card from '../../components/ui/Card'
import FeedbackAlert from '../../components/ui/FeedbackAlert'
import PageHeader from '../../components/ui/PageHeader'

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
  const allowed = ['ADMIN', 'LIBRARY_MANAGER', 'LIBRARIAN'].includes(getCurrentUser()?.role ?? '')
  const [state, setState] = useState<DashboardState>({
    status: 'loading',
    data: null,
  })

  const [errorNotice, setErrorNotice] = useState('')

  const loadStats = async () => {
    setErrorNotice('')
    setState({ status: 'loading', data: null })

    if (!allowed) return
    try {
      const data = await getDashboardStats()
      setState({ status: 'success', data })
    } catch {
      setErrorNotice('Không thể tải số liệu tổng quan từ hệ thống. Vui lòng thử lại.')
      setState({ status: 'error', data: null })
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
      icon: UsersRound,
      iconClass: 'bg-blue-50 text-blue-600',
    },
    {
      title: 'Bạn đọc hoạt động',
      value: state.data?.activeReaders,
      description: 'Tài khoản bạn đọc đang hoạt động',
      icon: UserRoundCheck,
      iconClass: 'bg-emerald-50 text-emerald-600',
    },
    {
      title: 'Thẻ thư viện',
      value: state.data?.issuedLibraryCards,
      description: 'Tổng số thẻ đã được cấp',
      icon: CreditCard,
      iconClass: 'bg-violet-50 text-violet-600',
    },
    {
      title: 'Chờ xử lý',
      value: state.data?.pendingReaderRequests,
      description: 'Hồ sơ bạn đọc đang chờ duyệt',
      icon: UserRoundSearch,
      iconClass: 'bg-amber-50 text-amber-600',
    },
  ]

  if (!allowed) return <p role="alert" className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm font-semibold text-red-700">Bạn không có quyền xem thống kê quản trị.</p>

  return (
    <div>
      <PageHeader
        title="Tổng quan"
        description="Theo dõi nhanh các số liệu quan trọng của hệ thống thư viện."
        action={(
          <Button
            type="button"
            variant="secondary"
            onClick={() => void loadStats()}
            loading={state.status === 'loading'}
          >
            <RefreshCw size={16} />
            Làm mới
          </Button>
        )}
      />

      {errorNotice && <FeedbackAlert message={errorNotice} tone="error" onDismiss={() => setErrorNotice('')} className="mb-5" />}
      {state.status === 'error' && <p role="status" className="mb-5 rounded-xl bg-slate-50 p-4 text-sm text-slate-700">
        Chưa tải được số liệu tổng quan. Nhấn “Làm mới” để thử lại.
      </p>}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {stats.map((item) => {
          const Icon = item.icon
          return (
            <Card key={item.title} className="p-5">
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0">
                  <div className="text-sm font-semibold text-slate-600">
                    {item.title}
                  </div>
                  <div className="mt-2 text-3xl font-bold tracking-tight text-slate-950">
                    {state.status === 'loading'
                      ? '...'
                      : state.status === 'success'
                        ? item.value ?? 0
                        : '—'}
                  </div>
                </div>
                <div className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-xl ${item.iconClass}`}>
                  <Icon size={21} />
                </div>
              </div>
              <div className="mt-3 border-t border-slate-100 pt-3 text-xs leading-5 text-slate-500">
                {item.description}
              </div>
            </Card>
          )
        })}
      </div>

      <Card className="mt-6 overflow-hidden">
        <div className="border-b border-slate-200 bg-slate-50/70 px-5 py-4">
          <h3 className="font-bold text-slate-900">Phạm vi hệ thống</h3>
        </div>
        <div className="p-5">
          <p className="text-sm leading-6 text-slate-600">
            Quản lý tài khoản, bạn đọc, thẻ thư viện, chính sách mượn, danh mục sách,
            bản sao và các nghiệp vụ đặt giữ trong một giao diện thống nhất.
          </p>
        </div>
      </Card>
    </div>
  )
}
