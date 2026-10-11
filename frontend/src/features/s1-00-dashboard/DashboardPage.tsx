import { Link, Navigate } from 'react-router-dom'
import { ArrowRight, BookOpen, Clock3 } from 'lucide-react'
import { navItems } from '../../app/featureRegistry'
import { visibleNavigation } from '../../app/navigation'
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
  const currentUser = getCurrentUser()
  const quickActions = visibleNavigation(navItems, currentUser?.role).filter(item => ['/loans/direct', '/loans/receive-return', '/readers', '/cataloging', '/reservations/ready-for-pickup', '/library-cards'].includes(item.to))
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

  if (currentUser?.role === 'READER') return <Navigate to="/catalog" replace />
  if (!allowed) return <p role="alert" className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm font-semibold text-red-700">Bạn không có quyền xem thống kê quản trị.</p>

  return (
    <div>
      <div className="dashboard-welcome"><div><p className="eyebrow">KHÔNG GIAN LÀM VIỆC</p><h2>Chào {currentUser?.fullName || 'bạn'},<br />một ngày làm việc hiệu quả.</h2><p>Tra cứu nhanh, xử lý rõ ràng, kết nối bạn đọc với thư viện.</p></div><BookOpen size={100} strokeWidth={1} aria-hidden="true" /></div>
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
            <Card key={item.title} className="stat-card p-5">
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0">
                  <div className="text-sm font-semibold text-slate-600">
                    {item.title}
                  </div>
                  <div className="mt-2 text-3xl font-bold tracking-tight text-slate-950">
                    {state.status === 'loading'
                      ? <span className="stat-skeleton" aria-label="Đang tải chỉ số" />
                      : state.status === 'success'
                        ? item.value?.toLocaleString('vi-VN') ?? '—'
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

      <div className="dashboard-detail-grid">
        <Card className="p-5 sm:p-6"><h2 className="text-lg font-bold text-slate-900">Tác vụ nhanh</h2><p className="mt-1 text-sm text-slate-500">Đi thẳng đến công việc cần xử lý.</p><div className="quick-actions">{quickActions.map(item => { const Icon = item.icon; return <Link to={item.to} key={item.to}><span><Icon size={20} /></span><strong>{item.label}</strong><ArrowRight size={16} /></Link> })}</div></Card>
        <Card className="p-5 sm:p-6"><div className="flex items-center gap-2"><Clock3 size={20} className="text-blue-600" /><h2 className="text-lg font-bold text-slate-900">Hồ sơ cần xử lý</h2></div>
          {state.status === 'success' ? <><p className="pending-count">{state.data.pendingReaderRequests.toLocaleString('vi-VN')}<span> hồ sơ chờ duyệt</span></p><p className="text-sm leading-6 text-slate-600">{state.data.pendingReaderRequests > 0 ? 'Kiểm tra thông tin bạn đọc và giấy tờ tại quầy trước khi cấp thẻ.' : 'Hiện không có hồ sơ bạn đọc đang chờ duyệt.'}</p><Link className="text-link mt-5" to="/readers">Xem danh sách bạn đọc <ArrowRight size={16} /></Link></> : <p className="mt-5 text-sm text-slate-500">{state.status === 'loading' ? 'Đang tải hồ sơ chờ…' : 'Chưa có dữ liệu. Nhấn Làm mới để thử lại.'}</p>}
        </Card>
      </div>
    </div>
  )
}
